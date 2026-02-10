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
public class ArquivoResponse {

    private String id;
    private String sessaoId;
    private String nomeOriginal;
    private long tamanhoBytes;
    private String tipoMime;
    private StatusArquivo status;
    private String remetenteId;
    private double progressoUpload;
    private int totalChunks;
    private int chunksRecebidos;
    private String mensagemErro;
    private Instant criadoEm;
    private Instant atualizadoEm;
    private Boolean conversivel;
    private String arquivoOriginalId;
    private String formatoConvertido;
    private String tag;
    private Integer otimizacaoNivel;
    private Long tamanhoOriginalBytes;
}
