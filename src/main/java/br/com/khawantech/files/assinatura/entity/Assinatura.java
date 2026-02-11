package br.com.khawantech.files.assinatura.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "assinaturas")
public class Assinatura {

    @Id
    private String id;

    @Indexed
    private String usuarioId;

    private String planoId;

    private String planoNome;

    private String referenciaExterna;

    private String assinaturaExternaId;

    private String cobrancaExternaId;

    private String qrCodeImageUrl;

    private String brCode;

    private String pagamentoLinkUrl;

    @Builder.Default
    private StatusAssinatura status = StatusAssinatura.PENDENTE;

    private Instant periodoInicio;

    private Instant periodoFim;

    private Instant pagamentoExpiraEm;

    private Instant pagamentoCriadoEm;

    @Builder.Default
    private boolean celebracaoExibida = false;

    private String ultimoEvento;

    @CreatedDate
    private Instant criadaEm;

    @LastModifiedDate
    private Instant atualizadaEm;

    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
