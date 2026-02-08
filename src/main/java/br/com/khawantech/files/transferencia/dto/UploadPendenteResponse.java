package br.com.khawantech.files.transferencia.dto;

import java.time.Instant;
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
public class UploadPendenteResponse {

    private String arquivoId;
    private String sessaoId;
    private String nomeOriginal;
    private Long tamanhoBytes;
    private String tipoMime;
    private String hashConteudo;
    private Integer totalChunks;
    private Integer chunkSizeBytes;
    private Set<Integer> chunksRecebidos;
    private Double progressoPorcentagem;
    private StatusArquivo status;
    private Instant criadoEm;
}
