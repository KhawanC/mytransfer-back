package br.com.khawantech.files.transferencia.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacaoResponse {

    private TipoNotificacao tipo;
    private String sessaoId;
    private String mensagem;
    private Object dados;
    private Instant timestamp;

    public enum TipoNotificacao {
        USUARIO_ENTROU,
        USUARIO_SAIU,
        SOLICITACAO_ENTRADA,
        ENTRADA_APROVADA,
        ENTRADA_REJEITADA,
        SESSAO_ENCERRADA,
        SESSAO_EXPIRADA,
        UPLOAD_INICIADO,
        UPLOAD_PROGRESSO,
        UPLOAD_COMPLETO,
        UPLOAD_ERRO,
        ARQUIVO_DISPONIVEL
    }
}
