package br.com.khawantech.files.transferencia.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusSessao;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.repository.ChatLeituraRepository;
import br.com.khawantech.files.transferencia.repository.ChatMensagemRepository;
import br.com.khawantech.files.transferencia.repository.ChunkArquivoRepository;
import br.com.khawantech.files.transferencia.repository.SessaoRepository;
import br.com.khawantech.files.user.entity.UserType;
import br.com.khawantech.files.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoCleanupService {

    private final SessaoRepository sessaoRepository;
    private final ArquivoRepository arquivoRepository;
    private final ChunkArquivoRepository chunkArquivoRepository;
    private final SessaoRedisService sessaoRedisService;
    private final ArquivoRedisService arquivoRedisService;
    private final ProgressoUploadRedisService progressoRedisService;
    private final ChatMensagemRepository chatMensagemRepository;
    private final ChatLeituraRepository chatLeituraRepository;
    private final MinioService minioService;
    private final WebSocketNotificationService notificationService;
    private final TransferenciaProperties properties;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void executarLimpeza() {
        log.info("Iniciando limpeza programada de sessões");

        try {
            expirarSessoesAtivas();
            limparSessoesExpiradas();
            limparUsuariosConvidadosExpirados();
        } catch (Exception e) {
            log.error("Erro durante limpeza de sessões: {}", e.getMessage(), e);
        }

        log.info("Limpeza programada finalizada");
    }

    private void expirarSessoesAtivas() {
        Instant agora = Instant.now();

        List<Sessao> sessoesParaExpirar = sessaoRepository.findByStatusIn(
            List.of(StatusSessao.AGUARDANDO, StatusSessao.AGUARDANDO_APROVACAO, StatusSessao.ATIVA)
        );

        for (Sessao sessao : sessoesParaExpirar) {
            if (sessao.getExpiraEm() != null && agora.isAfter(sessao.getExpiraEm())) {
                try {
                    expirarSessao(sessao);
                } catch (Exception e) {
                    log.error("Erro ao expirar sessão {}: {}", sessao.getId(), e.getMessage());
                }
            }
        }
    }

    private void expirarSessao(Sessao sessao) {
        sessao.setStatus(StatusSessao.EXPIRADA);
        sessao.setAtualizadaEm(Instant.now());
        sessaoRepository.save(sessao);

        sessaoRedisService.atualizarSessao(sessao);

        notificationService.notificarSessaoExpirada(sessao.getId());

        log.info("Sessão expirada: {}", sessao.getId());
    }

    private void limparSessoesExpiradas() {
        Instant limiteRemocao = Instant.now().minusMillis(properties.getCacheTtlMs());

        List<Sessao> sessoesParaRemover = sessaoRepository.findByStatusIn(
            List.of(StatusSessao.EXPIRADA, StatusSessao.ENCERRADA)
        );

        for (Sessao sessao : sessoesParaRemover) {
            if (sessao.getAtualizadaEm() != null && sessao.getAtualizadaEm().isBefore(limiteRemocao)) {
                try {
                    removerSessaoCompleta(sessao);
                } catch (Exception e) {
                    log.error("Erro ao remover sessão {}: {}", sessao.getId(), e.getMessage());
                }
            }
        }
    }

    private void removerSessaoCompleta(Sessao sessao) {
        log.info("Removendo sessão completa: {}", sessao.getId());

        List<Arquivo> arquivos = arquivoRepository.findBySessaoId(sessao.getId());
        for (Arquivo arquivo : arquivos) {
            chunkArquivoRepository.deleteByArquivoId(arquivo.getId());
            progressoRedisService.limparProgresso(arquivo.getId());
            arquivoRedisService.removerArquivo(arquivo.getId(), arquivo.getHashConteudo());
        }

        arquivoRepository.deleteBySessaoId(sessao.getId());

        chatMensagemRepository.deleteBySessaoId(sessao.getId());
        chatLeituraRepository.deleteBySessaoId(sessao.getId());

        minioService.deletarArquivosSessao(sessao.getId());

        sessaoRedisService.invalidarSessao(sessao.getId(), sessao.getHashConexao());
        sessaoRepository.delete(sessao);

        log.info("Sessão {} removida completamente", sessao.getId());
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void limparArquivosOrfaos() {
        log.info("Iniciando limpeza de arquivos órfãos");

        try {
            Instant limite = Instant.now().minus(2, ChronoUnit.HOURS);

            List<Arquivo> arquivosOrfaos = arquivoRepository.findByStatus(
                br.com.khawantech.files.transferencia.entity.StatusArquivo.ENVIANDO
            );

            for (Arquivo arquivo : arquivosOrfaos) {
                if (arquivo.getCriadoEm() != null && arquivo.getCriadoEm().isBefore(limite)) {
                    log.warn("Arquivo órfão encontrado: {} - criado em {}", 
                        arquivo.getId(), arquivo.getCriadoEm());

                    chunkArquivoRepository.deleteByArquivoId(arquivo.getId());
                    progressoRedisService.limparProgresso(arquivo.getId());
                    arquivoRedisService.removerArquivo(arquivo.getId(), arquivo.getHashConteudo());
                    arquivoRepository.delete(arquivo);

                    log.info("Arquivo órfão removido: {}", arquivo.getId());
                }
            }
        } catch (Exception e) {
            log.error("Erro durante limpeza de arquivos órfãos: {}", e.getMessage(), e);
        }

        log.info("Limpeza de arquivos órfãos finalizada");
    }

    private void limparUsuariosConvidadosExpirados() {
        log.info("Iniciando limpeza de usuários convidados expirados");

        try {
            Instant limiteExpiracao = Instant.now().minus(1, ChronoUnit.HOURS);

            List<br.com.khawantech.files.user.entity.User> usuariosExpirados = userRepository.findAll().stream()
                .filter(user -> user.getUserType() == UserType.GUEST)
                .filter(user -> user.getGuestCreatedAt() != null)
                .filter(user -> user.getGuestCreatedAt().isBefore(limiteExpiracao))
                .toList();

            int totalRemovidos = 0;

            for (br.com.khawantech.files.user.entity.User usuario : usuariosExpirados) {
                try {
                    List<Sessao> sessoesDoGuest = sessaoRepository
                        .findByUsuarioCriadorIdOrUsuarioConvidadoId(
                            usuario.getId(), 
                            usuario.getId()
                        );

                    for (Sessao sessao : sessoesDoGuest) {
                        try {
                            removerSessaoCompleta(sessao);
                        } catch (Exception e) {
                            log.error("Erro ao remover sessão {} do guest {}: {}", 
                                sessao.getId(), usuario.getId(), e.getMessage());
                        }
                    }

                    userRepository.delete(usuario);
                    totalRemovidos++;

                    log.info("Usuário guest expirado removido: {} ({}), criado em {}", 
                        usuario.getName(), usuario.getId(), usuario.getGuestCreatedAt());

                } catch (Exception e) {
                    log.error("Erro ao remover usuário guest {}: {}", 
                        usuario.getId(), e.getMessage());
                }
            }

            if (totalRemovidos > 0) {
                log.info("Total de usuários convidados expirados removidos: {}", totalRemovidos);
            }

        } catch (Exception e) {
            log.error("Erro durante limpeza de usuários convidados: {}", e.getMessage(), e);
        }

        log.info("Limpeza de usuários convidados finalizada");
    }
}
