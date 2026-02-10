package br.com.khawantech.files.assinatura.dto;

import java.time.Instant;

import br.com.khawantech.files.assinatura.entity.StatusAssinatura;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssinaturaStatusResponse {
    private String assinaturaId;
    private String planoId;
    private String planoNome;
    private StatusAssinatura status;
    private Instant periodoInicio;
    private Instant periodoFim;
    private String brCode;
    private String qrCodeImageUrl;
    private String paymentLinkUrl;
    private Instant pagamentoExpiraEm;
    private boolean cancelarAoFinalPeriodo;
    private boolean celebracaoExibida;
}
