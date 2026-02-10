package br.com.khawantech.files.assinatura.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.khawantech.files.assinatura.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssinaturaExpiracaoScheduler {

    private final AssinaturaService assinaturaService;

    @Scheduled(cron = "0 0 0 * * *")
    public void expirarAssinaturas() {
        try {
            int atualizadas = assinaturaService.expirarAssinaturas();
            if (atualizadas > 0) {
                log.info("Assinaturas expiradas: {}", atualizadas);
            }
        } catch (Exception e) {
            log.error("Erro ao expirar assinaturas", e);
        }
    }
}
