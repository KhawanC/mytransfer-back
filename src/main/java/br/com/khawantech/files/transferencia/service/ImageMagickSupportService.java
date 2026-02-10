package br.com.khawantech.files.transferencia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class ImageMagickSupportService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private volatile Map<String, String> formatModesByNameUpper;

    public boolean supportsRead(String formatName) {
        String mode = getMode(formatName);
        return mode != null && mode.indexOf('r') >= 0;
    }

    public boolean supportsWrite(String formatName) {
        String mode = getMode(formatName);
        return mode != null && mode.indexOf('w') >= 0;
    }

    public String getMode(String formatName) {
        if (formatName == null || formatName.isBlank()) {
            return null;
        }
        Map<String, String> modes = ensureLoaded();
        return modes.get(formatName.trim().toUpperCase(Locale.ROOT));
    }

    private Map<String, String> ensureLoaded() {
        Map<String, String> cached = formatModesByNameUpper;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (formatModesByNameUpper != null) {
                return formatModesByNameUpper;
            }
            formatModesByNameUpper = Collections.unmodifiableMap(loadFormats());
            return formatModesByNameUpper;
        }
    }

    private Map<String, String> loadFormats() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(resolveMagickBinary(), "-list", "format");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);

            boolean finished = process.waitFor(DEFAULT_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Timeout ao executar magick -list format");
                return Map.of();
            }

            int exitCode = process.exitValue();
            String text = output.toString(StandardCharsets.UTF_8);
            if (exitCode != 0) {
                log.warn("Falha ao executar magick -list format (exitCode={}). Saída: {}", exitCode, truncate(text));
                return Map.of();
            }

            return parseFormatList(text);
        } catch (Exception e) {
            log.warn("Não foi possível detectar formatos suportados pelo ImageMagick: {}", e.getMessage());
            return Map.of();
        }
    }

    private String resolveMagickBinary() {
        Path linuxMagick = Path.of("/usr/bin/magick");
        if (Files.isExecutable(linuxMagick)) {
            return linuxMagick.toString();
        }
        return "magick";
    }

    private Map<String, String> parseFormatList(String output) {
        Map<String, String> modes = new HashMap<>();

        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("Format") || trimmed.startsWith("--")) {
                continue;
            }

            String[] parts = trimmed.split("\\s+");
            if (parts.length < 3) {
                continue;
            }

            String formatToken = normalizeFormatName(parts[0]);
            String modeToken = parts[2];

            if (!formatToken.isEmpty() && !modeToken.isEmpty()) {
                modes.put(formatToken.toUpperCase(Locale.ROOT), modeToken);
            }
        }

        return modes;
    }

    private String normalizeFormatName(String token) {
        if (token == null) {
            return "";
        }
        return token.replace("*", "").replace("+", "").trim();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int max = 500;
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
