package br.com.khawantech.files.transferencia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArquivoCompletoEvent implements Serializable {

    private String arquivoId;
    private String sessaoId;
    private String nomeOriginal;
    private long tamanhoBytes;
    private String tipoMime;
    private String caminhoMinio;
    private String remetenteId;
}
