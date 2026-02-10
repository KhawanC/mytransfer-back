package br.com.khawantech.files.transferencia.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ArquivoSecurityPolicyServiceTest {

    private static Object newPolicy() {
        try {
            Class<?> clazz = Class.forName("br.com.khawantech.files.transferencia.service.ArquivoSecurityPolicyService");
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object avaliar(Object policy, String tipoMimeInformado, String tipoMimeDetectado, Map<String, String> metadados) {
        try {
            Method method = policy.getClass().getMethod("avaliar", String.class, String.class, Map.class);
            return method.invoke(policy, tipoMimeInformado, tipoMimeDetectado, metadados);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean permitido(Object decision) {
        try {
            Method method = decision.getClass().getMethod("permitido");
            return (boolean) method.invoke(decision);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String motivo(Object decision) {
        try {
            Method method = decision.getClass().getMethod("motivo");
            return (String) method.invoke(decision);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void deveBloquearQuandoMimeInformadoDivergeDoDetectado() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "image/png", "application/pdf", Map.of());

        assertFalse(permitido(decision));
        assertNotNull(motivo(decision));
    }

    @Test
    void devePermitirQuandoMimeInformadoEhOctetStream() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "application/octet-stream", "application/pdf", Map.of());

        assertTrue(permitido(decision));
        assertNull(motivo(decision));
    }

    @Test
    void devePermitirQuandoMimeEhEquivalenteMp4Quicktime() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "video/mp4", "video/quicktime", Map.of("Content-Type", "video/mp4"));

        assertTrue(permitido(decision));
        assertNull(motivo(decision));
    }

    @Test
    void devePermitirQuandoMimeVideoDetectadoEhOctetStreamParaEvitarFalsoPositivo() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "video/mp4", "application/octet-stream", Map.of());

        assertTrue(permitido(decision));
        assertNull(motivo(decision));
    }

    @Test
    void devePermitirQuandoMimeVideoInformadoEDetectadoSaoAmbosVideoMesmoSeDiferentes() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "video/webm", "video/x-matroska", Map.of());

        assertTrue(permitido(decision));
        assertNull(motivo(decision));
    }

    @Test
    void deveBloquearMacroEnabledMesmoQuandoDetectadoEhZip() {
        Object policy = newPolicy();
        Object decision = avaliar(policy, "application/vnd.ms-word.document.macroenabled.12", "application/zip", Map.of());

        assertFalse(permitido(decision));
        assertNotNull(motivo(decision));
    }
}
