package br.com.khawantech.files.transferencia.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArquivoSecurityEvent implements Serializable {

    private String arquivoId;
    private String sessaoId;
    private String remetenteId;
    private String nomeOriginal;
    private String tipoMimeInformado;
    private int totalChunks;
}
