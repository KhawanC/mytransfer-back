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
public class WebhookAssinaturaEvent {

    private String payload;

    private String signature;

    private Instant receivedAt;
}
