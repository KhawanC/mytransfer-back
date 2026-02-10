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
import br.com.khawantech.files.transferencia.dto.SessaoEstatisticasResponse;
import br.com.khawantech.files.transferencia.dto.SessaoResponse;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.entity.StatusSessao;
import br.com.khawantech.files.transferencia.exception.SessaoExpiradaException;
import br.com.khawantech.files.transferencia.exception.SessaoLotadaException;
import br.com.khawantech.files.transferencia.exception.SessaoNaoEncontradaException;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.repository.SessaoRepository;
import br.com.khawantech.files.auth.exception.AuthenticationException;
import br.com.khawantech.files.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final ArquivoRepository arquivoRepository;
    private final SessaoRedisService sessaoRedisService;
    private final QRCodeService qrCodeService;
    private final TransferenciaProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final LockRedisService lockRedisService;
    private final UserRepository userRepository;
    private final WebSocketNotificationService webSocketNotificationService;

    @Transactional
    public SessaoResponse criarSessao(String usuarioCriadorId) {
        validarUsuarioSemSessaoAtiva(usuarioCriadorId);
        
        br.com.khawantech.files.user.entity.User usuario = userRepository.findById(usuarioCriadorId)
            .orElseThrow(() -> new AuthenticationException("Sessão expirada"));
        
        TransferenciaProperties.UserLimits limites = properties.getLimitsForUserType(usuario.getUserType());
        
        Sessao sessao = Sessao.builder()
            .usuarioCriadorId(usuarioCriadorId)
            .status(StatusSessao.AGUARDANDO)
            .criadaEm(Instant.now())
            .expiraEm(Instant.now().plusMillis(limites.sessaoDuracaoMs()))
            .hashExpiraEm(Instant.now().plusSeconds(20))
            .build();

        sessao.generateId();
        sessao.generateHashConexao();

        sessao = sessaoRepository.save(sessao);
        sessaoRedisService.salvarSessao(sessao);

        String qrCodeBase64 = qrCodeService.gerarQRCodeBase64(sessao.getHashConexao());

        log.info("Sessão criada: {} por usuário {} ({}): duração {}min", 
                 sessao.getId(), usuarioCriadorId, usuario.getUserType(), limites.sessaoDuracaoMinutos());

        return toSessaoResponse(sessao, qrCodeBase64, usuarioCriadorId);
    }

    @Transactional
    public SessaoResponse entrarSessao(String hashConexao, String usuarioConvidadoId) {
        Sessao sessao = buscarPorHash(hashConexao);

        boolean jaEstaAssociado = usuarioConvidadoId.equals(sessao.getUsuarioConvidadoId()) ||
                                  usuarioConvidadoId.equals(sessao.getUsuarioConvidadoPendenteId());

        if (jaEstaAssociado) {
            log.info("Usuário {} já está associado à sessão {}, retornando sessão existente", 
                     usuarioConvidadoId, sessao.getId());
            return toSessaoResponse(sessao, null, usuarioConvidadoId);
        }

        validarSessaoParaEntrada(sessao, usuarioConvidadoId);

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a entrada na sessão. Tente novamente.");
        }

        try {
            br.com.khawantech.files.user.entity.User usuario = userRepository.findById(usuarioConvidadoId)
                .orElseThrow(() -> new AuthenticationException("Sessão expirada"));

            sessao.setUsuarioConvidadoPendenteId(usuarioConvidadoId);
            sessao.setNomeUsuarioConvidadoPendente(usuario.getName());
            sessao.setStatus(StatusSessao.AGUARDANDO_APROVACAO);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, StatusSessao.AGUARDANDO, "Usuário solicitou entrada na sessão");

            webSocketNotificationService.notificarSolicitacaoEntradaCriador(
                sessao.getUsuarioCriadorId(),
                sessao.getId(),
                usuario.getName()
            );

            log.info("Usuário {} solicitou entrada na sessão {} e está aguardando aprovação", usuarioConvidadoId, sessao.getId());

            return toSessaoResponse(sessao, null, usuarioConvidadoId);
        } finally {
            lockRedisService.liberarLock(lockRedisService.getLockSessao(sessao.getId()), lockId);
        }
    }

    @Transactional
    public SessaoResponse aprovarEntrada(String sessaoId, String usuarioCriadorId) {
        Sessao sessao = buscarPorId(sessaoId);

        if (!sessao.getUsuarioCriadorId().equals(usuarioCriadorId)) {
            throw new IllegalArgumentException("Apenas o criador da sessão pode aprovar a entrada");
        }

        if (sessao.getStatus() != StatusSessao.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Não há solicitação de entrada pendente para esta sessão");
        }

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a aprovação. Tente novamente.");
        }

        try {
            sessao.setUsuarioConvidadoId(sessao.getUsuarioConvidadoPendenteId());
            sessao.setUsuarioConvidadoPendenteId(null);
            sessao.setNomeUsuarioConvidadoPendente(null);
            sessao.setStatus(StatusSessao.ATIVA);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, StatusSessao.AGUARDANDO_APROVACAO, "Entrada aprovada pelo criador");

            log.info("Entrada do usuário {} aprovada na sessão {}", sessao.getUsuarioConvidadoId(), sessaoId);

            return toSessaoResponse(sessao, null, usuarioCriadorId);
        } finally {
            lockRedisService.liberarLock(lockRedisService.getLockSessao(sessao.getId()), lockId);
        }
    }

    @Transactional
    public void rejeitarEntrada(String sessaoId, String usuarioCriadorId) {
        Sessao sessao = buscarPorId(sessaoId);

        if (!sessao.getUsuarioCriadorId().equals(usuarioCriadorId)) {
            throw new IllegalArgumentException("Apenas o criador da sessão pode rejeitar a entrada");
        }

        if (sessao.getStatus() != StatusSessao.AGUARDANDO_APROVACAO) {
            throw new IllegalStateException("Não há solicitação de entrada pendente para esta sessão");
        }

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a rejeição. Tente novamente.");
        }

        try {
            String usuarioRejeitadoId = sessao.getUsuarioConvidadoPendenteId();

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
    public void sairDaSessao(String sessaoId, String usuarioConvidadoId) {
        Sessao sessao = buscarPorId(sessaoId);

        boolean eConvidadoPendente = usuarioConvidadoId.equals(sessao.getUsuarioConvidadoPendenteId());
        boolean eConvidadoAprovado = usuarioConvidadoId.equals(sessao.getUsuarioConvidadoId());
        
        if (!eConvidadoPendente && !eConvidadoAprovado) {
            throw new IllegalArgumentException("Apenas o usuário convidado pode sair da sessão");
        }

        if (sessao.getStatus() != StatusSessao.AGUARDANDO_APROVACAO && 
            sessao.getStatus() != StatusSessao.ATIVA) {
            throw new IllegalStateException("Não é possível sair da sessão no estado atual: " + sessao.getStatus());
        }

        String lockId = lockRedisService.adquirirLock(lockRedisService.getLockSessao(sessao.getId()));
        if (lockId == null) {
            throw new RuntimeException("Não foi possível processar a saída da sessão. Tente novamente.");
        }

        try {
            StatusSessao statusAnterior = sessao.getStatus();
            
            if (eConvidadoPendente) {
                sessao.setUsuarioConvidadoPendenteId(null);
                sessao.setNomeUsuarioConvidadoPendente(null);
                log.info("Usuário convidado pendente {} saiu da sessão {}", usuarioConvidadoId, sessaoId);
            } else {
                sessao.setUsuarioConvidadoId(null);
                log.info("Usuário convidado {} saiu da sessão {}", usuarioConvidadoId, sessaoId);
            }

            sessao.setStatus(StatusSessao.AGUARDANDO);
            sessao.setAtualizadaEm(Instant.now());

            sessao = sessaoRepository.save(sessao);
            sessaoRedisService.atualizarSessao(sessao);

            publicarAtualizacaoSessao(sessao, statusAnterior, "Usuário convidado saiu da sessão");

            webSocketNotificationService.notificarUsuarioSaiu(sessaoId, usuarioConvidadoId);
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
        Sessao sessao = sessaoCache.orElseGet(() ->
            sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new SessaoNaoEncontradaException("Sessão não encontrada: " + sessaoId))
        );

        if (sessao.hashExpirado()) {
            sessao = renovarHashSessao(sessao);
        }
        
        return sessao;
    }

    public Sessao buscarPorHash(String hashConexao) {
        Optional<Sessao> sessaoCache = sessaoRedisService.buscarPorHash(hashConexao);
        Sessao sessao = sessaoCache.orElseGet(() ->
            sessaoRepository.findByHashConexao(hashConexao)
                .orElseThrow(() -> new SessaoNaoEncontradaException("Sessão não encontrada com hash: " + hashConexao))
        );

        if (sessao.hashExpirado()) {
            sessao = renovarHashSessao(sessao);
        }
        
        return sessao;
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
    
    public void validarPodeUpload(Sessao sessao) {
        if (sessao.getStatus() != StatusSessao.ATIVA && sessao.getStatus() != StatusSessao.AGUARDANDO) {
            throw new IllegalStateException("Upload só é permitido em sessões ativas ou aguardando participantes. Status atual: " + sessao.getStatus());
        }

        if (sessao.getExpiraEm() != null && Instant.now().isAfter(sessao.getExpiraEm())) {
            expirarSessao(sessao);
            throw new SessaoExpiradaException("Sessão expirada: " + sessao.getId());
        }
    }

    public void validarLimiteArquivos(Sessao sessao) {
        br.com.khawantech.files.user.entity.User usuario = userRepository.findById(sessao.getUsuarioCriadorId())
            .orElseThrow(() -> new RuntimeException("Usuário criador não encontrado"));
        
        TransferenciaProperties.UserLimits limites = properties.getLimitsForUserType(usuario.getUserType());
        
        if (limites.hasUnlimitedFiles()) {
            log.debug("Usuário {} tem arquivos ilimitados (PREMIUM)", usuario.getId());
            return;
        }
        
        if (!sessao.podeReceberArquivos(limites.maxArquivos())) {
            throw new SessaoLotadaException(
                String.format("Limite de arquivos atingido para tipo %s: %d", 
                              usuario.getUserType(), limites.maxArquivos())
            );
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
    
    @Transactional
    public Sessao renovarHashSessao(Sessao sessao) {
        String hashAntigo = sessao.getHashConexao();

        sessaoRedisService.invalidarSessao(sessao.getId(), hashAntigo);

        sessao.regenerateHashConexao();

        sessao = sessaoRepository.save(sessao);
        sessaoRedisService.salvarSessao(sessao);

        webSocketNotificationService.notificarHashAtualizado(
            sessao.getId(), 
            sessao.getHashConexao(), 
            sessao.getHashExpiraEm()
        );
        
        log.info("Hash da sessão {} renovado de {} para {}", 
            sessao.getId(), hashAntigo, sessao.getHashConexao());
            
        return sessao;
    }
    
    @Transactional
    public void renovarHashSessoes() {
        List<StatusSessao> statusesAtivos = List.of(StatusSessao.AGUARDANDO, StatusSessao.AGUARDANDO_APROVACAO, StatusSessao.ATIVA);
        List<Sessao> sessoesAtivas = sessaoRepository.findByStatusIn(statusesAtivos);
        
        for (Sessao sessao : sessoesAtivas) {
            if (sessao.hashExpirado()) {
                renovarHashSessao(sessao);
            }
        }
    }

    public List<SessaoResponse> listarSessoesUsuario(String usuarioId) {
        List<Sessao> sessoes = sessaoRepository.findSessoesDoUsuario(usuarioId);

        sessoes.sort((s1, s2) -> s2.getCriadaEm().compareTo(s1.getCriadaEm()));
        
        log.info("Listando {} sessões para usuário {}", sessoes.size(), usuarioId);
        
        return sessoes.stream()
            .map(sessao -> toSessaoResponse(sessao, null, usuarioId))
            .toList();
    }
    
    private void validarUsuarioSemSessaoAtiva(String usuarioId) {
        List<StatusSessao> statusesAtivos = List.of(StatusSessao.AGUARDANDO, StatusSessao.AGUARDANDO_APROVACAO, StatusSessao.ATIVA);

        for (StatusSessao status : statusesAtivos) {
            List<Sessao> sessoesComoCriador = sessaoRepository.findByUsuarioCriadorIdAndStatus(usuarioId, status);
            if (!sessoesComoCriador.isEmpty()) {
                throw new IllegalStateException("Você já possui uma sessão ativa");
            }
        }

        for (StatusSessao status : statusesAtivos) {
            List<Sessao> sessoesComoConvidado = sessaoRepository.findByUsuarioConvidadoIdAndStatus(usuarioId, status);
            if (!sessoesComoConvidado.isEmpty()) {
                throw new IllegalStateException("Você já possui uma sessão ativa");
            }
        }
    }

    private SessaoResponse toSessaoResponse(Sessao sessao, String qrCodeBase64, String usuarioId) {
        boolean estaAtiva = sessao.getStatus() == StatusSessao.ATIVA || 
                           sessao.getStatus() == StatusSessao.AGUARDANDO || 
                           sessao.getStatus() == StatusSessao.AGUARDANDO_APROVACAO;
        
        boolean podeUpload = sessao.getStatus() == StatusSessao.ATIVA ||
                            sessao.getStatus() == StatusSessao.AGUARDANDO;
        
        boolean podeEncerrar = estaAtiva && usuarioId != null && 
                              (usuarioId.equals(sessao.getUsuarioCriadorId()) || 
                               usuarioId.equals(sessao.getUsuarioConvidadoId()));
        
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
            .hashExpiraEm(sessao.getHashExpiraEm())
            .podeUpload(podeUpload)
            .podeEncerrar(podeEncerrar)
            .estaAtiva(estaAtiva)
            .build();
    }

    public br.com.khawantech.files.transferencia.dto.SessaoLimitesResponse buscarLimitesSessao(String sessaoId) {
        Sessao sessao = buscarPorId(sessaoId);
        
        br.com.khawantech.files.user.entity.User usuarioCriador = userRepository.findById(sessao.getUsuarioCriadorId())
            .orElseThrow(() -> new RuntimeException("Usuário criador não encontrado"));
        
        TransferenciaProperties.UserLimits limites = properties.getLimitsForUserType(usuarioCriador.getUserType());
        
        return br.com.khawantech.files.transferencia.dto.SessaoLimitesResponse.builder()
            .maxArquivos(limites.maxArquivos())
            .maxTamanhoMb(limites.maxTamanhoMb())
            .duracaoMinutos(limites.sessaoDuracaoMinutos())
            .userType(usuarioCriador.getUserType().name())
            .arquivosIlimitados(limites.hasUnlimitedFiles())
            .build();
    }

    public boolean podeAdicionarArquivo(String sessaoId) {
        Sessao sessao = buscarPorId(sessaoId);
        
        br.com.khawantech.files.user.entity.User usuarioCriador = userRepository.findById(sessao.getUsuarioCriadorId())
            .orElseThrow(() -> new RuntimeException("Usuário criador não encontrado"));
        
        TransferenciaProperties.UserLimits limites = properties.getLimitsForUserType(usuarioCriador.getUserType());
        
        if (limites.hasUnlimitedFiles()) {
            return true;
        }
        
        long quantidadeArquivos = arquivoRepository.countBySessaoIdAndStatusIn(
            sessaoId, 
            List.of(StatusArquivo.COMPLETO, StatusArquivo.ENVIANDO, StatusArquivo.PROCESSANDO)
        );
        
        return quantidadeArquivos < limites.maxArquivos();
    }

    public SessaoEstatisticasResponse obterEstatisticasSessao(String sessaoId) {
        Sessao sessao = buscarPorId(sessaoId);
        
        br.com.khawantech.files.user.entity.User usuarioCriador = userRepository.findById(sessao.getUsuarioCriadorId())
            .orElseThrow(() -> new RuntimeException("Usuário criador não encontrado"));
        
        TransferenciaProperties.UserLimits limites = properties.getLimitsForUserType(usuarioCriador.getUserType());
        
        int quantidadeArquivos = (int) arquivoRepository.countBySessaoIdAndStatusIn(
            sessaoId, 
            List.of(StatusArquivo.COMPLETO, StatusArquivo.ENVIANDO, StatusArquivo.PROCESSANDO)
        );
        
        long tamanhoTotal = arquivoRepository.findBySessaoIdAndStatusIn(
            sessaoId, 
            List.of(StatusArquivo.COMPLETO)
        ).stream()
            .mapToLong(arquivo -> arquivo.getTamanhoBytes())
            .sum();
        
        int espacoDisponivel = limites.hasUnlimitedFiles() 
            ? Integer.MAX_VALUE 
            : limites.maxArquivos() - quantidadeArquivos;
        
        return SessaoEstatisticasResponse.builder()
            .quantidadeArquivos(quantidadeArquivos)
            .limiteArquivos(limites.maxArquivos())
            .tamanhoTotalBytes(tamanhoTotal)
            .limiteTamanhoBytes(limites.maxTamanhoBytes())
            .espacoDisponivel(Math.max(0, espacoDisponivel))
            .build();
    }
}
