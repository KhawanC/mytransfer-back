package br.com.khawantech.files.transferencia.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "transferencia")
public class TransferenciaProperties {

    private double sessaoTtlHoras = 2;
    private double cacheTtlHoras = 3;
    private int maxArquivos = 50;
    private long maxTamanhoMb = 1024;
    private int chunkSizeMb = 5;
    private String minioBucket = "transferencias";

    public long getMaxTamanhoBytes() {
        return maxTamanhoMb * 1024 * 1024;
    }

    public long getChunkSizeBytes() {
        return chunkSizeMb * 1024L * 1024L;
    }

    public long getSessaoTtlMs() {
        return (long) (sessaoTtlHoras * 60 * 60 * 1000);
    }

    public long getCacheTtlMs() {
        return (long) (cacheTtlHoras * 60 * 60 * 1000);
    }
}
