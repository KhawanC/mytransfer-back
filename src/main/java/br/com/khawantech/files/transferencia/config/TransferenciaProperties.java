package br.com.khawantech.files.transferencia.config;

import br.com.khawantech.files.user.entity.UserType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "transferencia")
public class TransferenciaProperties {

    private double cacheTtlHoras = 3;
    private int chunkSizeMb = 5;
    private String minioBucket = "transferencias";
    
    private GuestLimits guest = new GuestLimits();
    private FreeLimits free = new FreeLimits();
    private PremiumLimits premium = new PremiumLimits();

    @Data
    public static class GuestLimits {
        private int sessaoDuracaoMinutos = 10;
        private int maxArquivos = 10;
        private long maxTamanhoMb = 150;
        private int maxParticipantes = 2;
    }

    @Data
    public static class FreeLimits {
        private int sessaoDuracaoMinutos = 30;
        private int maxArquivos = 25;
        private long maxTamanhoMb = 250;
        private int maxParticipantes = 2;
    }

    @Data
    public static class PremiumLimits {
        private int sessaoDuracaoMinutos = 300;
        private Integer maxArquivos = null;
        private long maxTamanhoMb = 5120;
        private int maxParticipantes = 10;
    }

    public UserLimits getLimitsForUserType(UserType userType) {
        return switch (userType) {
            case GUEST -> new UserLimits(
                guest.getSessaoDuracaoMinutos(),
                guest.getMaxArquivos(),
                guest.getMaxTamanhoMb(),
                guest.getMaxParticipantes()
            );
            case FREE -> new UserLimits(
                free.getSessaoDuracaoMinutos(),
                free.getMaxArquivos(),
                free.getMaxTamanhoMb(),
                free.getMaxParticipantes()
            );
            case PREMIUM -> new UserLimits(
                premium.getSessaoDuracaoMinutos(),
                premium.getMaxArquivos(),
                premium.getMaxTamanhoMb(),
                premium.getMaxParticipantes()
            );
        };
    }

    public long getChunkSizeBytes() {
        return chunkSizeMb * 1024L * 1024L;
    }

    public long getCacheTtlMs() {
        return (long) (cacheTtlHoras * 60 * 60 * 1000);
    }

    public record UserLimits(
        int sessaoDuracaoMinutos,
        Integer maxArquivos,
        long maxTamanhoMb,
        int maxParticipantes
    ) {
        public long maxTamanhoBytes() {
            return maxTamanhoMb * 1024 * 1024;
        }

        public long sessaoDuracaoMs() {
            return sessaoDuracaoMinutos * 60L * 1000L;
        }

        public boolean hasUnlimitedFiles() {
            return maxArquivos == null;
        }

        public int maxConvidados() {
            return Math.max(0, maxParticipantes - 1);
        }
    }
}
