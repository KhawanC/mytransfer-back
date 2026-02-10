package br.com.khawantech.files.transferencia.service;

import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ArquivoSecurityPolicyService {

    private static final String OCTET_STREAM = "application/octet-stream";

    private static final Set<String> MIME_DENYLIST = Set.of(
        "application/x-dosexec",
        "application/x-msdownload",
        "application/x-executable",
        "application/x-sh",
        "text/x-shellscript",
        "application/x-bat",
        "application/x-msi",
        "application/java-archive",
        "application/vnd.microsoft.portable-executable",
        "application/x-mach-binary"
    );

    private static final Set<String> MIME_MACRO_ENABLED = Set.of(
        "application/vnd.ms-word.document.macroenabled.12",
        "application/vnd.ms-excel.sheet.macroenabled.12",
        "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        "application/vnd.ms-excel.addin.macroenabled.12",
        "application/vnd.ms-word.template.macroenabled.12",
        "application/vnd.ms-powerpoint.slideshow.macroenabled.12"
    );

    public Decision avaliar(String tipoMimeInformado, String tipoMimeDetectado, Map<String, String> metadados) {
        String informado = normalize(tipoMimeInformado);
        String detectado = normalize(tipoMimeDetectado);

        if (MIME_DENYLIST.contains(detectado)) {
            String motivo = "Arquivo bloqueado: tipo não permitido (" + detectado + ")";
            log.warn("Policy bloqueou por denylist: informado={} detectado={} metadados={}", informado, detectado, pickMetadados(metadados));
            return Decision.bloquear(motivo);
        }

        if (MIME_MACRO_ENABLED.contains(detectado)) {
            String motivo = "Arquivo bloqueado: possível macro habilitada (" + detectado + ")";
            log.warn("Policy bloqueou por macro-enabled: informado={} detectado={} metadados={}", informado, detectado, pickMetadados(metadados));
            return Decision.bloquear(motivo);
        }

        if (!OCTET_STREAM.equals(informado) && !informado.equals(detectado)) {
            String motivo = "Arquivo bloqueado: tipo informado divergente do detectado";
            log.warn("Policy bloqueou por divergência MIME: informado={} detectado={} metadados={}", informado, detectado, pickMetadados(metadados));
            return Decision.bloquear(motivo);
        }

        return Decision.permitir();
    }

    private static Map<String, String> pickMetadados(Map<String, String> metadados) {
        if (metadados == null || metadados.isEmpty()) {
            return Map.of();
        }

        Map<String, String> picked = new LinkedHashMap<>();

        putIfPresent(picked, metadados, "Content-Type");
        putIfPresent(picked, metadados, "resourceName");
        putIfPresent(picked, metadados, "Content-Encoding");
        putIfPresent(picked, metadados, "X-Parsed-By");
        putIfPresent(picked, metadados, "X-TIKA:Parsed-By");
        putIfPresent(picked, metadados, "tika:content_handler");
        putIfPresent(picked, metadados, "Content-Length");

        if (picked.isEmpty()) {
            picked.put("_keys", String.join(",", metadados.keySet().stream().limit(16).toList()));
        }

        return picked;
    }

    private static void putIfPresent(Map<String, String> out, Map<String, String> in, String key) {
        String value = in.get(key);
        if (value != null && !value.isBlank()) {
            out.put(key, value);
        }
    }

    private static String normalize(String mime) {
        if (mime == null || mime.isBlank()) {
            return OCTET_STREAM;
        }
        String lower = mime.strip().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "image/jpg" -> "image/jpeg";
            case "application/x-zip-compressed" -> "application/zip";
            default -> lower;
        };
    }

    public record Decision(boolean permitido, String motivo) {
        public static Decision permitir() {
            return new Decision(true, null);
        }

        public static Decision bloquear(String motivo) {
            return new Decision(false, motivo);
        }
    }
}
