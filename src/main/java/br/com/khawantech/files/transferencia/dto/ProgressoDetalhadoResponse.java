package br.com.khawantech.files.transferencia.dto;

import java.util.Set;

import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressoDetalhadoResponse {

    private String arquivoId;
    private String sessaoId;
    private String nomeArquivo;
    private Long tamanhoBytes;
    private Integer totalChunks;
    private Integer chunkSizeBytes;
    private Set<Integer> chunksRecebidos;
    private Double progressoPorcentagem;
    private StatusArquivo status;
    private Boolean uploadValido;
}
