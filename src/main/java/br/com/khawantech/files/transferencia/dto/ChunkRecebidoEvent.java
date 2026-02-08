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
public class ChunkRecebidoEvent implements Serializable {

    private String arquivoId;
    private String sessaoId;
    private int numeroChunk;
    private int totalChunks;
    private String hashChunk;
    private String caminhoMinio;
    private long tamanhoBytes;
    private String usuarioId;
}
