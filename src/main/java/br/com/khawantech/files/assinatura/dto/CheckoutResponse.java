package br.com.khawantech.files.assinatura.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private String assinaturaId;
    private String planoId;
    private String brCode;
    private String qrCodeImageUrl;
    private String paymentLinkUrl;
    private Instant expiraEm;
}
