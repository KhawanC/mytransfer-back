package br.com.khawantech.files.transferencia.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.khawantech.files.transferencia.service.SessaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashRenovacaoScheduler {

    private final SessaoService sessaoService;

    @Scheduled(fixedDelay = 20000, initialDelay = 20000)
    public void renovarHashesExpirados() {
        log.debug("Executando renovação de hashes de sessões");
        try {
            sessaoService.renovarHashSessoes();
        } catch (Exception e) {
            log.error("Erro ao renovar hashes de sessões", e);
        }
    }
}
