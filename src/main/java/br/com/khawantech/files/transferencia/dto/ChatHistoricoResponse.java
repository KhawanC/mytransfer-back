package br.com.khawantech.files.transferencia.dto;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoricoResponse {

    private List<ChatMensagemResponse> mensagens;
    private Instant ultimoLeituraEm;
    private int naoLidas;
}
