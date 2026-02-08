package br.com.khawantech.files.transferencia.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chunks_arquivos")
@CompoundIndexes({
    @CompoundIndex(name = "arquivo_chunk_idx", def = "{'arquivoId': 1, 'numeroChunk': 1}", unique = true)
})
public class ChunkArquivo implements Serializable {

    @Id
    private String id;

    private String arquivoId;

    private int numeroChunk;

    private int totalChunks;

    private long tamanhoBytes;

    private String hashChunk;

    private String caminhoMinio;

    @CreatedDate
    private Instant recebidoEm;

    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
