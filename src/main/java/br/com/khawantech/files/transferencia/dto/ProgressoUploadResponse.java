package br.com.khawantech.files.transferencia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressoUploadResponse {

    private String arquivoId;
    private String sessaoId;
    private String nomeArquivo;
    private int chunkAtual;
    private int totalChunks;
    private double progressoPorcentagem;
    private boolean completo;
    private String urlDownload;
}
