package br.com.khawantech.files.transferencia.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.khawantech.files.transferencia.dto.FormatoImagem;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arquivos")
@CompoundIndexes({
    @CompoundIndex(name = "sessao_status_idx", def = "{'sessaoId': 1, 'status': 1}"),
    @CompoundIndex(name = "hash_conteudo_idx", def = "{'hashConteudo': 1}"),
    @CompoundIndex(name = "arquivo_original_idx", def = "{'arquivoOriginalId': 1}")
})
public class Arquivo implements Serializable {

    @Id
    private String id;

    @Indexed
    private String sessaoId;

    private String nomeOriginal;

    private String hashConteudo;

    private long tamanhoBytes;

    private String tipoMime;

    private String caminhoMinio;

    @Indexed
    @Builder.Default
    private StatusArquivo status = StatusArquivo.PENDENTE;

    private String remetenteId;

    private int totalChunks;

    private int chunksRecebidos;

    @Builder.Default
    private double progressoUpload = 0.0;

    @Builder.Default
    private Boolean conversivel = false;

    private String arquivoOriginalId;

    private String formatoConvertido;

    private String mensagemErro;

    @CreatedDate
    private Instant criadoEm;

    @LastModifiedDate
    private Instant atualizadoEm;

    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public void atualizarProgresso(int chunksRecebidos) {
        this.chunksRecebidos = chunksRecebidos;
        if (this.totalChunks > 0) {
            this.progressoUpload = (double) chunksRecebidos / totalChunks * 100.0;
        }
    }

    public boolean uploadCompleto() {
        return this.chunksRecebidos >= this.totalChunks && this.totalChunks > 0;
    }

    public boolean isConversao() {
        return this.arquivoOriginalId != null;
    }

    public List<FormatoImagem> getFormatosDisponiveis() {
        if (!Boolean.TRUE.equals(this.conversivel)) {
            return List.of();
        }
        return FormatoImagem.getFormatosDisponiveis(this.tipoMime);
    }
}
