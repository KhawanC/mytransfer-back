package br.com.khawantech.files.transferencia.entity;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_leituras")
public class ChatLeitura implements Serializable {

    @Id
    private String id;

    @Indexed
    private String sessaoId;

    @Indexed
    private String usuarioId;

    private Instant ultimoLeituraEm;

    @Indexed
    private Instant expiraEm;

    public ChatLeitura() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessaoId() {
        return sessaoId;
    }

    public void setSessaoId(String sessaoId) {
        this.sessaoId = sessaoId;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Instant getUltimoLeituraEm() {
        return ultimoLeituraEm;
    }

    public void setUltimoLeituraEm(Instant ultimoLeituraEm) {
        this.ultimoLeituraEm = ultimoLeituraEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Instant expiraEm) {
        this.expiraEm = expiraEm;
    }
}
