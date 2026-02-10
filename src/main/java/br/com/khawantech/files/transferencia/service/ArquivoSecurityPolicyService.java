package br.com.khawantech.files.transferencia.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

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
            return Decision.bloquear("Arquivo bloqueado: tipo não permitido (" + detectado + ")");
        }

        if (MIME_MACRO_ENABLED.contains(detectado)) {
            return Decision.bloquear("Arquivo bloqueado: possível macro habilitada (" + detectado + ")");
        }

        if (!OCTET_STREAM.equals(informado) && !informado.equals(detectado)) {
            return Decision.bloquear("Arquivo bloqueado: tipo informado divergente do detectado");
        }

        return Decision.permitir();
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
