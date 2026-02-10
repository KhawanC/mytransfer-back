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
public class ChatMensagemResponse {

    private String id;
    private String sessaoId;
    private String remetenteId;
    private String remetenteNome;
    private String conteudo;
    private Instant criadoEm;
}
