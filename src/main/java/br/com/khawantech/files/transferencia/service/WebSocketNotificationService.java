package br.com.khawantech.files.transferencia.service;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import br.com.khawantech.files.transferencia.dto.NotificacaoResponse;
import br.com.khawantech.files.transferencia.dto.ProgressoUploadResponse;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notificarSessao(String sessaoId, NotificacaoResponse notificacao) {
        String destination = "/topic/sessao/" + sessaoId;
        messagingTemplate.convertAndSend(destination, notificacao);
        log.debug("Notificação enviada para sessão {}: {}", sessaoId, notificacao.getTipo());
    }

    public void notificarUsuario(String usuarioId, NotificacaoResponse notificacao) {
        String destination = "/queue/notificacoes";
        messagingTemplate.convertAndSendToUser(usuarioId, destination, notificacao);
        log.debug("Notificação enviada para usuário {}: {}", usuarioId, notificacao.getTipo());
    }

    public void notificarProgresso(String sessaoId, ProgressoUploadResponse progresso) {
        String destination = "/topic/sessao/" + sessaoId + "/progresso";
        messagingTemplate.convertAndSend(destination, progresso);
    }

    public void notificarUsuarioEntrou(String sessaoId, String usuarioConvidadoId) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.USUARIO_ENTROU)
            .sessaoId(sessaoId)
            .mensagem("Usuário entrou na sessão")
            .dados(usuarioConvidadoId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarSolicitacaoEntrada(String sessaoId, String usuarioConvidadoPendenteId, String nomeUsuario) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.SOLICITACAO_ENTRADA)
            .sessaoId(sessaoId)
            .mensagem(nomeUsuario + " solicitou entrada na sessão")
            .dados(new SolicitacaoEntrada(usuarioConvidadoPendenteId, nomeUsuario))
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }
    
    public void notificarSolicitacaoEntradaCriador(String usuarioCriadorId, String sessaoId, String nomeUsuario) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.SOLICITACAO_ENTRADA_CRIADOR)
            .sessaoId(sessaoId)
            .mensagem(nomeUsuario + " solicitou entrada na sessão")
            .dados(sessaoId)
            .timestamp(Instant.now())
            .build();

        notificarUsuario(usuarioCriadorId, notificacao);
    }

    public void notificarEntradaAprovada(String sessaoId, String usuarioConvidadoId) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.ENTRADA_APROVADA)
            .sessaoId(sessaoId)
            .mensagem("Sua entrada na sessão foi aprovada")
            .dados(usuarioConvidadoId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarEntradaRejeitada(String sessaoId) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.ENTRADA_REJEITADA)
            .sessaoId(sessaoId)
            .mensagem("Sua entrada na sessão foi rejeitada")
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarUsuarioSaiu(String sessaoId, String usuarioId) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.USUARIO_SAIU)
            .sessaoId(sessaoId)
            .mensagem("Usuário saiu da sessão")
            .dados(usuarioId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarSessaoEncerrada(String sessaoId, String motivo) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.SESSAO_ENCERRADA)
            .sessaoId(sessaoId)
            .mensagem(motivo)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarSessaoExpirada(String sessaoId) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.SESSAO_EXPIRADA)
            .sessaoId(sessaoId)
            .mensagem("Sessão expirada")
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }
    
    public void notificarHashAtualizado(String sessaoId, String novoHash, Instant hashExpiraEm) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.HASH_ATUALIZADO)
            .sessaoId(sessaoId)
            .mensagem("Hash da sessão foi atualizado")
            .dados(new HashData(novoHash, hashExpiraEm))
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarUploadIniciado(String sessaoId, String arquivoId, String nomeArquivo) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.UPLOAD_INICIADO)
            .sessaoId(sessaoId)
            .mensagem("Upload iniciado: " + nomeArquivo)
            .dados(arquivoId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarUploadCompleto(String sessaoId, String arquivoId, String nomeArquivo) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.UPLOAD_COMPLETO)
            .sessaoId(sessaoId)
            .mensagem("Upload completo: " + nomeArquivo)
            .dados(arquivoId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarArquivoDisponivel(String sessaoId, String arquivoId, String nomeArquivo, String urlDownload, boolean conversivel) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.ARQUIVO_DISPONIVEL)
            .sessaoId(sessaoId)
            .mensagem("Arquivo disponível para download: " + nomeArquivo)
            .dados(new ArquivoDisponivel(arquivoId, nomeArquivo, urlDownload, conversivel))
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarErroUpload(String sessaoId, String arquivoId, String erro) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.UPLOAD_ERRO)
            .sessaoId(sessaoId)
            .mensagem("Erro no upload: " + erro)
            .dados(arquivoId)
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
    }

    public void notificarConversaoConcluida(String sessaoId, Arquivo arquivoConvertido) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.ARQUIVO_CONVERTIDO)
            .sessaoId(sessaoId)
            .mensagem("Arquivo convertido com sucesso: " + arquivoConvertido.getNomeOriginal())
            .dados(new ArquivoConvertido(
                arquivoConvertido.getId(),
                arquivoConvertido.getArquivoOriginalId(),
                arquivoConvertido.getNomeOriginal(),
                arquivoConvertido.getFormatoConvertido(),
                arquivoConvertido.getTamanhoBytes()
            ))
            .timestamp(Instant.now())
            .build();

        notificarSessao(sessaoId, notificacao);
        log.info("Notificação de conversão enviada para sessão {}: arquivo {}", sessaoId, arquivoConvertido.getId());
    }

    public record ArquivoDisponivel(String arquivoId, String nomeArquivo, String urlDownload, boolean conversivel) {}
    
    public record ArquivoConvertido(String arquivoId, String arquivoOriginalId, String nomeArquivo, 
                                    String formato, Long tamanhoBytes) {}
    
    public record SolicitacaoEntrada(String usuarioConvidadoPendenteId, String nomeUsuario) {}
    
    public record HashData(String hash, Instant expiraEm) {}
}
