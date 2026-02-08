package br.com.khawantech.files.transferencia.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.ArquivoCompletoEvent;
import br.com.khawantech.files.transferencia.dto.ArquivoResponse;
import br.com.khawantech.files.transferencia.dto.ChunkRecebidoEvent;
import br.com.khawantech.files.transferencia.dto.EnviarChunkRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadRequest;
import br.com.khawantech.files.transferencia.dto.IniciarUploadResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoDetalhadoResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoUploadResponse;
import br.com.khawantech.files.transferencia.dto.UploadPendenteResponse;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.ChunkArquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.exception.ArquivoMuitoGrandeException;
import br.com.khawantech.files.transferencia.exception.ChunkInvalidoException;
import br.com.khawantech.files.transferencia.exception.HashInvalidoException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.repository.ChunkArquivoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArquivoService {

    private final ArquivoRepository arquivoRepository;
    private final ChunkArquivoRepository chunkArquivoRepository;
    private final ArquivoRedisService arquivoRedisService;
    private final SessaoService sessaoService;
    private final MinioService minioService;
    private final HashService hashService;
    private final LockRedisService lockRedisService;
    private final ProgressoUploadRedisService progressoRedisService;
    private final RateLimitRedisService rateLimitRedisService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final DownloadTokenService downloadTokenService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public IniciarUploadResponse iniciarUpload(IniciarUploadRequest request, String usuarioId) {
        if (!rateLimitRedisService.verificarLimiteRequisicoes(usuarioId)) {
            throw new RuntimeException("Rate limit excedido. Aguarde alguns segundos.");
        }

        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());
        sessaoService.validarSessaoAtiva(sessao);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);
        sessaoService.validarLimiteArquivos(sessao);

        if (request.getTamanhoBytes() > properties.getMaxTamanhoBytes()) {
            throw new ArquivoMuitoGrandeException(
                "Arquivo muito grande. Máximo permitido: " + properties.getMaxTamanhoMb() + " MB"
            );
        }

        Optional<Arquivo> arquivoExistente = verificarDeduplicacao(request.getSessaoId(), request.getHashConteudo());
        if (arquivoExistente.isPresent()) {
            Arquivo existente = arquivoExistente.get();
            log.info("Arquivo duplicado detectado na mesma sessão: {}", existente.getId());

            return IniciarUploadResponse.builder()
                .arquivoId(existente.getId())
                .sessaoId(request.getSessaoId())
                .nomeArquivo(existente.getNomeOriginal())
                .tamanhoBytes(existente.getTamanhoBytes())
                .totalChunks(existente.getTotalChunks())
                .chunkSizeBytes(properties.getChunkSizeBytes())
                .status(StatusArquivo.COMPLETO)
                .arquivoDuplicado(true)
                .arquivoExistenteId(existente.getId())
                .criadoEm(existente.getCriadoEm())
                .build();
        }

        int totalChunks = calcularTotalChunks(request.getTamanhoBytes());

        Arquivo arquivo = Arquivo.builder()
            .sessaoId(request.getSessaoId())
            .nomeOriginal(request.getNomeArquivo())
            .hashConteudo(request.getHashConteudo())
            .tamanhoBytes(request.getTamanhoBytes())
            .tipoMime(request.getTipoMime())
            .status(StatusArquivo.PENDENTE)
            .remetenteId(usuarioId)
            .totalChunks(totalChunks)
            .chunksRecebidos(0)
            .progressoUpload(0.0)
            .criadoEm(Instant.now())
            .build();

        arquivo.generateId();
        arquivo = arquivoRepository.save(arquivo);
        arquivoRedisService.salvarArquivo(arquivo);

        log.info("Upload iniciado: {} para sessão: {}", arquivo.getId(), request.getSessaoId());

        return IniciarUploadResponse.builder()
            .arquivoId(arquivo.getId())
            .sessaoId(request.getSessaoId())
            .nomeArquivo(arquivo.getNomeOriginal())
            .tamanhoBytes(arquivo.getTamanhoBytes())
            .totalChunks(totalChunks)
            .chunkSizeBytes(properties.getChunkSizeBytes())
            .status(arquivo.getStatus())
            .arquivoDuplicado(false)
            .criadoEm(arquivo.getCriadoEm())
            .build();
    }

    @Transactional
    public ProgressoUploadResponse processarChunk(EnviarChunkRequest request, String usuarioId) {
        if (!rateLimitRedisService.verificarLimiteChunks(usuarioId)) {
            throw new RuntimeException("Rate limit de chunks excedido. Aguarde alguns segundos.");
        }

        Arquivo arquivo = buscarArquivoPorId(request.getArquivoId());
        Sessao sessao = sessaoService.buscarPorId(request.getSessaoId());

        sessaoService.validarSessaoAtiva(sessao);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        if (!arquivo.getSessaoId().equals(request.getSessaoId())) {
            throw new ChunkInvalidoException("Arquivo não pertence à sessão informada");
        }

        if (arquivo.getStatus() == StatusArquivo.COMPLETO) {
            return criarProgressoResponse(arquivo, true);
        }

        if (request.getNumeroChunk() < 0 || request.getNumeroChunk() >= arquivo.getTotalChunks()) {
            throw new ChunkInvalidoException("Número do chunk inválido: " + request.getNumeroChunk());
        }

        if (progressoRedisService.chunkJaRecebido(arquivo.getId(), request.getNumeroChunk())) {
            log.debug("Chunk {} já recebido para arquivo {}", request.getNumeroChunk(), arquivo.getId());
            return criarProgressoResponse(arquivo, false);
        }

        if (!hashService.verificarHashBase64(request.getDadosBase64(), request.getHashChunk())) {
            throw new HashInvalidoException("Hash do chunk não confere");
        }

        String lockKey = lockRedisService.getLockChunk(arquivo.getId(), request.getNumeroChunk());
        String lockId = lockRedisService.adquirirLock(lockKey);

        if (lockId == null) {
            throw new RuntimeException("Chunk está sendo processado. Aguarde.");
        }

        try {
            String caminhoMinio = minioService.uploadChunk(
                sessao.getId(),
                arquivo.getId(),
                request.getNumeroChunk(),
                request.getDadosBase64()
            );

            ChunkArquivo chunk = ChunkArquivo.builder()
                .arquivoId(arquivo.getId())
                .numeroChunk(request.getNumeroChunk())
                .totalChunks(arquivo.getTotalChunks())
                .tamanhoBytes(Base64.getDecoder().decode(request.getDadosBase64()).length)
                .hashChunk(request.getHashChunk())
                .caminhoMinio(caminhoMinio)
                .recebidoEm(Instant.now())
                .build();

            chunk.generateId();
            chunkArquivoRepository.save(chunk);

            progressoRedisService.registrarChunkRecebido(
                arquivo.getId(),
                request.getNumeroChunk(),
                arquivo.getTotalChunks()
            );

            ChunkRecebidoEvent event = ChunkRecebidoEvent.builder()
                .arquivoId(arquivo.getId())
                .sessaoId(sessao.getId())
                .numeroChunk(request.getNumeroChunk())
                .totalChunks(arquivo.getTotalChunks())
                .hashChunk(request.getHashChunk())
                .caminhoMinio(caminhoMinio)
                .tamanhoBytes(chunk.getTamanhoBytes())
                .usuarioId(usuarioId)
                .build();

            rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TRANSFERENCIA,
                RabbitConfig.ROUTING_KEY_CHUNK,
                event
            );

            int chunksRecebidos = progressoRedisService.getChunksRecebidos(arquivo.getId());
            boolean completo = chunksRecebidos >= arquivo.getTotalChunks();

            arquivo.atualizarProgresso(chunksRecebidos);
            if (completo) {
                arquivo.setStatus(StatusArquivo.PROCESSANDO);
            } else {
                arquivo.setStatus(StatusArquivo.ENVIANDO);
            }
            arquivo.setAtualizadoEm(Instant.now());

            arquivoRepository.save(arquivo);
            arquivoRedisService.atualizarArquivo(arquivo);

            if (completo) {
                finalizarUpload(arquivo, sessao);
            }

            return criarProgressoResponse(arquivo, completo);

        } finally {
            lockRedisService.liberarLock(lockKey, lockId);
        }
    }

    @Transactional
    public void finalizarUpload(Arquivo arquivo, Sessao sessao) {
        String lockKey = lockRedisService.getLockArquivo(arquivo.getId());
        String lockId = lockRedisService.adquirirLock(lockKey);

        if (lockId == null) {
            log.warn("Não foi possível adquirir lock para finalizar upload: {}", arquivo.getId());
            return;
        }

        try {
            String caminhoFinal = minioService.mergeChunks(
                sessao.getId(),
                arquivo.getId(),
                arquivo.getNomeOriginal(),
                arquivo.getTotalChunks(),
                arquivo.getTipoMime()
            );

            arquivo.setCaminhoMinio(caminhoFinal);
            arquivo.setStatus(StatusArquivo.COMPLETO);
            arquivo.setProgressoUpload(100.0);
            arquivo.setChunksRecebidos(arquivo.getTotalChunks());
            arquivo.setAtualizadoEm(Instant.now());

            arquivoRepository.save(arquivo);
            arquivoRedisService.atualizarArquivo(arquivo);

            sessaoService.incrementarArquivosTransferidos(sessao.getId());

            progressoRedisService.limparProgresso(arquivo.getId());

            ArquivoCompletoEvent event = ArquivoCompletoEvent.builder()
                .arquivoId(arquivo.getId())
                .sessaoId(sessao.getId())
                .nomeOriginal(arquivo.getNomeOriginal())
                .tamanhoBytes(arquivo.getTamanhoBytes())
                .tipoMime(arquivo.getTipoMime())
                .caminhoMinio(caminhoFinal)
                .remetenteId(arquivo.getRemetenteId())
                .build();

            rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TRANSFERENCIA,
                RabbitConfig.ROUTING_KEY_ARQUIVO,
                event
            );

            log.info("Upload finalizado: {} - {}", arquivo.getId(), arquivo.getNomeOriginal());

        } finally {
            lockRedisService.liberarLock(lockKey, lockId);
        }
    }

    public String gerarUrlDownload(String arquivoId, String usuarioId) {
        Arquivo arquivo = buscarArquivoPorId(arquivoId);

        Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        if (arquivo.getStatus() != StatusArquivo.COMPLETO) {
            throw new RuntimeException("Arquivo ainda não está disponível para download");
        }

        // Gera token temporário para download
        String token = downloadTokenService.gerarToken(arquivoId, usuarioId);
        
        // Retorna URL pública com token (válido por 5 minutos)
        return baseUrl + "/api/files/d/" + token;
    }

    public List<ArquivoResponse> listarArquivosSessao(String sessaoId, String usuarioId) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        return arquivoRepository.findBySessaoId(sessaoId).stream()
            .map(this::toArquivoResponse)
            .toList();
    }

    public Arquivo buscarArquivoPorId(String arquivoId) {
        Optional<Arquivo> arquivoCache = arquivoRedisService.buscarPorId(arquivoId);
        if (arquivoCache.isPresent()) {
            return arquivoCache.get();
        }

        return arquivoRepository.findById(arquivoId)
            .map(arquivo -> {
                arquivoRedisService.salvarArquivo(arquivo);
                return arquivo;
            })
            .orElseThrow(() -> new RuntimeException("Arquivo não encontrado: " + arquivoId));
    }

    /**
     * Verifica se já existe um arquivo completo com o mesmo hash APENAS na mesma sessão.
     * Arquivos duplicados em sessões diferentes ou com status diferente de COMPLETO são ignorados.
     */
    private Optional<Arquivo> verificarDeduplicacao(String sessaoId, String hashConteudo) {
        // Busca no cache Redis primeiro (filtrado por sessão)
        Optional<Arquivo> arquivoCache = arquivoRedisService.buscarPorHash(hashConteudo);
        if (arquivoCache.isPresent()) {
            Arquivo cached = arquivoCache.get();
            // Verifica se é da mesma sessão E está completo
            if (cached.getSessaoId().equals(sessaoId) && cached.getStatus() == StatusArquivo.COMPLETO) {
                return arquivoCache;
            }
        }

        // Busca no MongoDB filtrando por sessão + hash + status COMPLETO
        return arquivoRepository.findBySessaoIdAndHashConteudo(sessaoId, hashConteudo)
            .filter(a -> a.getStatus() == StatusArquivo.COMPLETO);
    }

    private int calcularTotalChunks(long tamanhoBytes) {
        return (int) Math.ceil((double) tamanhoBytes / properties.getChunkSizeBytes());
    }

    private ProgressoUploadResponse criarProgressoResponse(Arquivo arquivo, boolean completo) {
        String urlDownload = null;
        if (completo && arquivo.getCaminhoMinio() != null) {
            // Gera token temporário para download
            String token = downloadTokenService.gerarToken(arquivo.getId(), arquivo.getRemetenteId());
            urlDownload = baseUrl + "/api/files/d/" + token;
        }

        return ProgressoUploadResponse.builder()
            .arquivoId(arquivo.getId())
            .sessaoId(arquivo.getSessaoId())
            .nomeArquivo(arquivo.getNomeOriginal())
            .chunkAtual(arquivo.getChunksRecebidos())
            .totalChunks(arquivo.getTotalChunks())
            .progressoPorcentagem(arquivo.getProgressoUpload())
            .completo(completo)
            .urlDownload(urlDownload)
            .build();
    }

    private ArquivoResponse toArquivoResponse(Arquivo arquivo) {
        return ArquivoResponse.builder()
            .id(arquivo.getId())
            .sessaoId(arquivo.getSessaoId())
            .nomeOriginal(arquivo.getNomeOriginal())
            .tamanhoBytes(arquivo.getTamanhoBytes())
            .tipoMime(arquivo.getTipoMime())
            .status(arquivo.getStatus())
            .remetenteId(arquivo.getRemetenteId())
            .progressoUpload(arquivo.getProgressoUpload())
            .totalChunks(arquivo.getTotalChunks())
            .chunksRecebidos(arquivo.getChunksRecebidos())
            .criadoEm(arquivo.getCriadoEm())
            .atualizadoEm(arquivo.getAtualizadoEm())
            .build();
    }

    /**
     * Retorna o progresso detalhado de um upload, incluindo a lista de chunks já recebidos.
     * Usado para implementar upload resumable.
     */
    public ProgressoDetalhadoResponse getProgressoDetalhado(String arquivoId, String usuarioId) {
        Arquivo arquivo = buscarArquivoPorId(arquivoId);
        Sessao sessao = sessaoService.buscarPorId(arquivo.getSessaoId());
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        Set<Object> chunksSet = progressoRedisService.getChunksRecebidosSet(arquivoId);
        Set<Integer> chunksRecebidos = chunksSet.stream()
            .map(obj -> Integer.valueOf(obj.toString()))
            .collect(java.util.stream.Collectors.toSet());

        boolean uploadValido = arquivo.getStatus() == StatusArquivo.PENDENTE 
            || arquivo.getStatus() == StatusArquivo.ENVIANDO;

        return ProgressoDetalhadoResponse.builder()
            .arquivoId(arquivo.getId())
            .sessaoId(arquivo.getSessaoId())
            .nomeArquivo(arquivo.getNomeOriginal())
            .tamanhoBytes(arquivo.getTamanhoBytes())
            .totalChunks(arquivo.getTotalChunks())
            .chunkSizeBytes((int) properties.getChunkSizeBytes())
            .chunksRecebidos(chunksRecebidos)
            .progressoPorcentagem(arquivo.getProgressoUpload())
            .status(arquivo.getStatus())
            .uploadValido(uploadValido)
            .build();
    }

    /**
     * Busca uploads pendentes (incompletos) de um usuário em uma sessão.
     * Usado para permitir retomada de uploads após refresh da página.
     */
    public List<UploadPendenteResponse> buscarUploadsPendentes(String sessaoId, String usuarioId) {
        Sessao sessao = sessaoService.buscarPorId(sessaoId);
        sessaoService.validarUsuarioPertenceASessao(sessao, usuarioId);

        List<Arquivo> arquivosPendentes = arquivoRepository.findBySessaoIdAndRemetenteIdAndStatusIn(
            sessaoId,
            usuarioId,
            List.of(StatusArquivo.PENDENTE, StatusArquivo.ENVIANDO)
        );

        return arquivosPendentes.stream()
            .map(arquivo -> {
                Set<Object> chunksSet = progressoRedisService.getChunksRecebidosSet(arquivo.getId());
                Set<Integer> chunksRecebidos = chunksSet.stream()
                    .map(obj -> Integer.valueOf(obj.toString()))
                    .collect(java.util.stream.Collectors.toSet());

                return UploadPendenteResponse.builder()
                    .arquivoId(arquivo.getId())
                    .sessaoId(arquivo.getSessaoId())
                    .nomeOriginal(arquivo.getNomeOriginal())
                    .tamanhoBytes(arquivo.getTamanhoBytes())
                    .tipoMime(arquivo.getTipoMime())
                    .hashConteudo(arquivo.getHashConteudo())
                    .totalChunks(arquivo.getTotalChunks())
                    .chunkSizeBytes((int) properties.getChunkSizeBytes())
                    .chunksRecebidos(chunksRecebidos)
                    .progressoPorcentagem(arquivo.getProgressoUpload())
                    .status(arquivo.getStatus())
                    .criadoEm(arquivo.getCriadoEm())
                    .build();
            })
            .toList();
    }
}
