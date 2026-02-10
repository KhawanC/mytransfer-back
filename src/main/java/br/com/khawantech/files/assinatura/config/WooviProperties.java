package br.com.khawantech.files.assinatura.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "woovi")
public class WooviProperties {

    private String apiKey;
    private String webhookSignatureHeader = "x-webhook-signature";
}
