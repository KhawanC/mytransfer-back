package br.com.khawantech.files.transferencia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.dto.SessaoAtualizadaEvent;
import br.com.khawantech.files.transferencia.dto.SessaoResponse;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusSessao;
import br.com.khawantech.files.transferencia.exception.SessaoExpiradaException;
import br.com.khawantech.files.transferencia.exception.SessaoLotadaException;
import br.com.khawantech.files.transferencia.exception.SessaoNaoEncontradaException;
import br.com.khawantech.files.transferencia.repository.SessaoRepository;
import br.com.khawantech.files.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final SessaoRedisService sessaoRedisService;
    private final QRCodeService qrCodeService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final LockRedisService lockRedisService;
    private final UserRepository userRepository;

    @Transactional
    public SessaoResponse criarSessao(String usuarioCriadorId) {
        Sessao sessao = Sessao.builder()
            .usuarioCriadorId(usuarioCriadorId)
            .status(StatusSessao.AGUARDANDO)
            .criadaEm(Instant.now())
            .expiraEm(Instant.now().plusMillis(properties.getSessaoTtlMs()))
            .build();

        sessao.generateId();
        sessao.generateHashConexao();

        sessao = sessaoRepository.save(sessao);
        sessaoRedisService.salvarSessao(sessao);

        String qrCodeBase64 = qrCodeService.gerarQRCodeBase64(sessao.getHashConexao());

        log.info("Sessão criada: {} por usuário: {}", sessao.getId(), usuarioCriadorId);

        return SessaoResponse.builder()
            .id(sessao.getId())
            .hashConexao(sessao.getHashConexao())
            .qrCodeBase64(qrCodeBase64)
            .status(sessao.getStatus())
            .usuarioCriadorId(sessao.getUsuarioCriadorId())
            .criadaEm(sessao.getCriadaEm())
            .expiraEm(sessao.getExpiraEm())
            .build();
    }

    @Transactional
    public SessaoResponse entrarSessao(String hashConexao, String usuarioConvidadoId) {
        Sessao sessao = buscarPorHash(hashConexao);

        // Verifica se o usuário já está associado à sessão (convidado ou pendente)
        boolean jaEstaAssociado = usuarioConvidadoId.equals(sessao.getUsuarioConvidadoId()) ||
                                  usuarioConvidadoId.equals(sessao.getUsuarioConvidadoPendenteId());
        
        // Se já está associado, apenas retorna a sessão sem modificações
        if (jaEstaAssociado) {
            log.info("Usuário {} já está associado à sessão {}, retornando sessão existente", 
                     usuarioConvidadoId, sessao.getId());
            return toSessaoResponse(sessao, null);
        }

        // Se não está associado, valida a entrada normalmente
        validarSessaoParaEntrada(sessao, usuarioConvidadoId);

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a entrada na sessão. Tente novamente.");
        }

        try {
            // Busca o nome do usuário convidado
            var usuario = userRepository.findById(usuarioConvidadoId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            // Define o status como aguardando aprovação ao invés de ativar diretamente
            sessao.setUsuarioConvidadoPendenteId(usuarioConvidadoId);
            sessao.setNomeUsuarioConvidadoPendente(usuario.getName());
            sessao.setStatus(StatusSessao.AGUARDANDO_APROVACAO);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, StatusSessao.AGUARDANDO, "Usuário solicitou entrada na sessão");

            log.info("Usuário {} solicitou entrada na sessão {} e está aguardando aprovação", usuarioConvidadoId, sessao.getId());

            return toSessaoResponse(sessao, null);
        } finally {
            lockRedisService.liberarLock(lockRedisService.getLockSessao(sessao.getId()), lockId);
        }
    }

    @Transactional
    public SessaoResponse aprovarEntrada(String sessaoId, String usuarioCriadorId) {
        Sessao sessao = buscarPorId(sessaoId);

        // Valida que quem está aprovando é o criador da sessão
        if (!sessao.getUsuarioCriadorId().equals(usuarioCriadorId)) {
            throw new IllegalArgumentException("Apenas o criador da sessão pode aprovar a entrada");
        }

        // Valida que a sessão está aguardando aprovação
        if (sessao.getStatus() != StatusSessao.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Não há solicitação de entrada pendente para esta sessão");
        }

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a aprovação. Tente novamente.");
        }

        try {
            // Move o usuário pendente para convidado e ativa a sessão
            sessao.setUsuarioConvidadoId(sessao.getUsuarioConvidadoPendenteId());
            sessao.setUsuarioConvidadoPendenteId(null);
            sessao.setNomeUsuarioConvidadoPendente(null);
            sessao.setStatus(StatusSessao.ATIVA);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, StatusSessao.AGUARDANDO_APROVACAO, "Entrada aprovada pelo criador");

            log.info("Entrada do usuário {} aprovada na sessão {}", sessao.getUsuarioConvidadoId(), sessaoId);

            return toSessaoResponse(sessao, null);
        } finally {
            lockRedisService.liberarLock(lockRedisService.getLockSessao(sessao.getId()), lockId);
        }
    }

    @Transactional
    public void rejeitarEntrada(String sessaoId, String usuarioCriadorId) {
        Sessao sessao = buscarPorId(sessaoId);

        // Valida que quem está rejeitando é o criador da sessão
        if (!sessao.getUsuarioCriadorId().equals(usuarioCriadorId)) {
            throw new IllegalArgumentException("Apenas o criador da sessão pode rejeitar a entrada");
        }

        // Valida que a sessão está aguardando aprovação
        if (sessao.getStatus() != StatusSessao.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Não há solicitação de entrada pendente para esta sessão");
        }

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a rejeição. Tente novamente.");
        }

        try {
            String usuarioRejeitadoId = sessao.getUsuarioConvidadoPendenteId();
            
            // Remove o usuário pendente e volta ao estado aguardando
            sessao.setUsuarioConvidadoPendenteId(null);
            sessao.setNomeUsuarioConvidadoPendente(null);
            sessao.setStatus(StatusSessao.AGUARDANDO);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, StatusSessao.AGUARDANDO_APROVACAO, "Entrada rejeitada pelo criador");

            log.info("Entrada do usuário {} rejeitada na sessão {}", usuarioRejeitadoId, sessaoId);
        } finally {
            lockRedisService.liberarLock(lockRedisService.getLockSessao(sessao.getId()), lockId);
        }
    }

    @Transactional
    public void encerrarSessao(String sessaoId, String usuarioId) {
        Sessao sessao = buscarPorId(sessaoId);

        validarUsuarioPertenceASessao(sessao, usuarioId);

        StatusSessao statusAnterior = sessao.getStatus();
        sessao.setStatus(StatusSessao.ENCERRADA);
        sessao.setEncerradaEm(Instant.now());
        sessao.setAtualizadaEm(Instant.now());

        sessaoRepository.save(sessao);
        sessaoRedisService.atualizarSessao(sessao);

        publicarAtualizacaoSessao(sessao, statusAnterior, "Sessão encerrada pelo usuário " + usuarioId);

        log.info("Sessão {} encerrada pelo usuário {}", sessaoId, usuarioId);
    }

    public Sessao buscarPorId(String sessaoId) {
        Optional<Sessao> sessaoCache = sessaoRedisService.buscarPorId(sessaoId);
        if (sessaoCache.isPresent()) {
            return sessaoCache.get();
        }

        return sessaoRepository.findById(sessaoId)
            .map(sessao -> {
                sessaoRedisService.salvarSessao(sessao);
                return sessao;
            })
            .orElseThrow(() -> new SessaoNaoEncontradaException("Sessão não encontrada: " + sessaoId));
    }

    public Sessao buscarPorHash(String hashConexao) {
        Optional<Sessao> sessaoCache = sessaoRedisService.buscarPorHash(hashConexao);
        if (sessaoCache.isPresent()) {
            return sessaoCache.get();
        }

        return sessaoRepository.findByHashConexao(hashConexao)
            .map(sessao -> {
                sessaoRedisService.salvarSessao(sessao);
                return sessao;
            })
            .orElseThrow(() -> new SessaoNaoEncontradaException("Sessão não encontrada com hash: " + hashConexao));
    }

    public void validarSessaoAtiva(Sessao sessao) {
        if (!sessao.estaAtiva()) {
            if (sessao.getStatus() == StatusSessao.EXPIRADA) {
                throw new SessaoExpiradaException("Sessão expirada: " + sessao.getId());
            }
            throw new SessaoNaoEncontradaException("Sessão não está ativa: " + sessao.getId());
        }

        if (sessao.getExpiraEm() != null && Instant.now().isAfter(sessao.getExpiraEm())) {
            expirarSessao(sessao);
            throw new SessaoExpiradaException("Sessão expirada: " + sessao.getId());
        }
    }

    public void validarLimiteArquivos(Sessao sessao) {
        if (!sessao.podeReceberArquivos(properties.getMaxArquivos())) {
            throw new SessaoLotadaException("Limite de arquivos atingido: " + properties.getMaxArquivos());
        }
    }

    @Transactional
    public void incrementarArquivosTransferidos(String sessaoId) {
        Sessao sessao = buscarPorId(sessaoId);
        sessao.setTotalArquivosTransferidos(sessao.getTotalArquivosTransferidos() + 1);
        sessao.setAtualizadaEm(Instant.now());

        sessaoRepository.save(sessao);
        sessaoRedisService.atualizarSessao(sessao);
    }

    @Transactional
    public void expirarSessao(Sessao sessao) {
        StatusSessao statusAnterior = sessao.getStatus();
        sessao.setStatus(StatusSessao.EXPIRADA);
        sessao.setAtualizadaEm(Instant.now());

        sessaoRepository.save(sessao);
        sessaoRedisService.atualizarSessao(sessao);

        publicarAtualizacaoSessao(sessao, statusAnterior, "Sessão expirada automaticamente");

        log.info("Sessão {} expirada", sessao.getId());
    }

    public void validarUsuarioPertenceASessao(Sessao sessao, String usuarioId) {
        boolean pertence = usuarioId.equals(sessao.getUsuarioCriadorId()) ||
                          usuarioId.equals(sessao.getUsuarioConvidadoId()) ||
                          usuarioId.equals(sessao.getUsuarioConvidadoPendenteId());

        if (!pertence) {
            throw new SessaoNaoEncontradaException("Usuário não pertence à sessão");
        }
    }

    private void validarSessaoParaEntrada(Sessao sessao, String usuarioConvidadoId) {
        if (!sessao.estaAguardando()) {
            throw new SessaoLotadaException("Sessão já possui dois participantes ou não está disponível");
        }

        if (sessao.getUsuarioCriadorId().equals(usuarioConvidadoId)) {
            throw new IllegalArgumentException("Não é possível entrar na própria sessão");
        }

        if (sessao.getExpiraEm() != null && Instant.now().isAfter(sessao.getExpiraEm())) {
            expirarSessao(sessao);
            throw new SessaoExpiradaException("Sessão expirada");
        }
    }

    private void publicarAtualizacaoSessao(Sessao sessao, StatusSessao statusAnterior, String motivo) {
        SessaoAtualizadaEvent event = SessaoAtualizadaEvent.builder()
            .sessaoId(sessao.getId())
            .statusAnterior(statusAnterior)
            .statusNovo(sessao.getStatus())
            .usuarioConvidadoId(sessao.getUsuarioConvidadoId())
            .usuarioConvidadoPendenteId(sessao.getUsuarioConvidadoPendenteId())
            .nomeUsuarioConvidadoPendente(sessao.getNomeUsuarioConvidadoPendente())
            .totalArquivos(sessao.getTotalArquivosTransferidos())
            .motivo(motivo)
            .build();

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_TRANSFERENCIA,
            RabbitConfig.ROUTING_KEY_SESSAO,
            event
        );
    }

    public List<SessaoResponse> listarSessoesUsuario(String usuarioId) {
        List<Sessao> sessoes = sessaoRepository.findSessoesDoUsuario(usuarioId);
        
        // Ordena por data de criação decrescente
        sessoes.sort((s1, s2) -> s2.getCriadaEm().compareTo(s1.getCriadaEm()));
        
        log.info("Listando {} sessões para usuário {}", sessoes.size(), usuarioId);
        
        return sessoes.stream()
            .map(sessao -> toSessaoResponse(sessao, null))
            .toList();
    }

    private SessaoResponse toSessaoResponse(Sessao sessao, String qrCodeBase64) {
        return SessaoResponse.builder()
            .id(sessao.getId())
            .hashConexao(sessao.getHashConexao())
            .qrCodeBase64(qrCodeBase64)
            .status(sessao.getStatus())
            .usuarioCriadorId(sessao.getUsuarioCriadorId())
            .usuarioConvidadoId(sessao.getUsuarioConvidadoId())
            .usuarioConvidadoPendenteId(sessao.getUsuarioConvidadoPendenteId())
            .nomeUsuarioConvidadoPendente(sessao.getNomeUsuarioConvidadoPendente())
            .totalArquivosTransferidos(sessao.getTotalArquivosTransferidos())
            .criadaEm(sessao.getCriadaEm())
            .expiraEm(sessao.getExpiraEm())
            .build();
    }
}
