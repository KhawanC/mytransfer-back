package br.com.khawantech.files.transferencia.service;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
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

    private static final Map<String, String> MIME_EQUIVALENCE_GROUP;

    static {
        Map<String, String> groups = new HashMap<>();

        groups.put("video/mp4", "video-mp4");
        groups.put("video/quicktime", "video-mp4");

        groups.put("audio/mp4", "audio-mp4");
        groups.put("audio/x-m4a", "audio-mp4");
        groups.put("audio/m4a", "audio-mp4");

        groups.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "ooxml-zip");
        groups.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ooxml-zip");
        groups.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "ooxml-zip");
        groups.put("application/zip", "ooxml-zip");

        MIME_EQUIVALENCE_GROUP = Map.copyOf(groups);
    }

    public Decision avaliar(String tipoMimeInformado, String tipoMimeDetectado, Map<String, String> metadados) {
        String informado = normalize(tipoMimeInformado);
        String detectado = normalize(tipoMimeDetectado);

        if (MIME_DENYLIST.contains(informado) || MIME_DENYLIST.contains(detectado)) {
            return Decision.bloquear("Arquivo bloqueado: tipo não permitido (" + detectado + ")");
        }

        if (MIME_MACRO_ENABLED.contains(informado) || MIME_MACRO_ENABLED.contains(detectado)) {
            return Decision.bloquear("Arquivo bloqueado: possível macro habilitada (" + detectado + ")");
        }

        if (!OCTET_STREAM.equals(informado) && !informado.equals(detectado)) {
            if (isEquivalentMime(informado, detectado)) {
                return Decision.permitir();
            }
            return Decision.bloquear("Arquivo bloqueado: tipo informado divergente do detectado");
        }

        return Decision.permitir();
    }

    private static boolean isEquivalentMime(String informado, String detectado) {
        String g1 = MIME_EQUIVALENCE_GROUP.get(informado);
        if (g1 == null) {
            return false;
        }
        String g2 = MIME_EQUIVALENCE_GROUP.get(detectado);
        return g1.equals(g2);
    }



    private static String normalize(String mime) {
        if (mime == null || mime.isBlank()) {
            return OCTET_STREAM;
        }

        String stripped = mime.strip();
        int paramIdx = stripped.indexOf(';');
        if (paramIdx > 0) {
            stripped = stripped.substring(0, paramIdx).strip();
        }

        String lower = stripped.toLowerCase(Locale.ROOT);
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
