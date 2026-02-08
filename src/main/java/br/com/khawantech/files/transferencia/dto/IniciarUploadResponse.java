package br.com.khawantech.files.transferencia.dto;

import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IniciarUploadResponse {

    private String arquivoId;
    private String sessaoId;
    private String nomeArquivo;
    private long tamanhoBytes;
    private int totalChunks;
    private long chunkSizeBytes;
    private StatusArquivo status;
    private boolean arquivoDuplicado;
    private String arquivoExistenteId;
    private Instant criadoEm;
}
