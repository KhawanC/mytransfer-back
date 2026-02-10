package br.com.khawantech.files.transferencia.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class ArquivoSecurityPolicyServiceTest {

    @Test
    void deveBloquearQuandoMimeInformadoDivergeDoDetectado() {
        ArquivoSecurityPolicyService policy = new ArquivoSecurityPolicyService();

        ArquivoSecurityPolicyService.Decision decision = policy.avaliar(
            "image/png",
            "application/pdf",
            Map.of()
        );

        assertFalse(decision.permitido());
        assertNotNull(decision.motivo());
    }

    @Test
    void devePermitirQuandoMimeInformadoEhOctetStream() {
        ArquivoSecurityPolicyService policy = new ArquivoSecurityPolicyService();

        ArquivoSecurityPolicyService.Decision decision = policy.avaliar(
            "application/octet-stream",
            "application/pdf",
            Map.of()
        );

        assertTrue(decision.permitido());
        assertNull(decision.motivo());
    }

    @Test
    void devePermitirQuandoMimeEhEquivalenteMp4Quicktime() {
        ArquivoSecurityPolicyService policy = new ArquivoSecurityPolicyService();

        ArquivoSecurityPolicyService.Decision decision = policy.avaliar(
            "video/mp4",
            "video/quicktime",
            Map.of("Content-Type", "video/mp4")
        );

        assertTrue(decision.permitido());
        assertNull(decision.motivo());
    }

    @Test
    void deveBloquearMacroEnabledMesmoQuandoDetectadoEhZip() {
        ArquivoSecurityPolicyService policy = new ArquivoSecurityPolicyService();

        ArquivoSecurityPolicyService.Decision decision = policy.avaliar(
            "application/vnd.ms-word.document.macroenabled.12",
            "application/zip",
            Map.of()
        );

        assertFalse(decision.permitido());
        assertNotNull(decision.motivo());
    }
}
