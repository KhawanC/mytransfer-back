package br.com.khawantech.files.transferencia.service;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikaFileAnalysisService {

    private static final int MAX_METADATA_ENTRIES = 64;
    private static final int MAX_METADATA_VALUE_LENGTH = 512;

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public AnaliseTikaResponse analisar(byte[] prefixoBytes) {
        if (prefixoBytes == null) {
            prefixoBytes = new byte[0];
        }

        String mimeDetectado;
        try {
            mimeDetectado = tika.detect(prefixoBytes);
        } catch (Exception e) {
            log.warn("Falha ao detectar mime via Tika: prefixoBytes={} erro={} mensagem={}", prefixoBytes.length, e.getClass().getSimpleName(), e.getMessage());
            mimeDetectado = "application/octet-stream";
        }

        String normalized = normalizeMime(mimeDetectado);
        if (log.isDebugEnabled()) {
            log.debug("Tika detectou mime: prefixoBytes={} mime={} normalized={}", prefixoBytes.length, mimeDetectado, normalized);
        }

        Metadata metadata = new Metadata();
        try {
            parser.parse(new ByteArrayInputStream(prefixoBytes), new DefaultHandler(), metadata, new ParseContext());
        } catch (Exception e) {
            log.debug("Falha ao extrair metadata via Tika (prefixo): prefixoBytes={} erro={} mensagem={}", prefixoBytes.length, e.getClass().getSimpleName(), e.getMessage());
        }

        Map<String, String> metadados = toMap(metadata);

        return new AnaliseTikaResponse(normalized, metadados);
    }

    private static Map<String, String> toMap(Metadata metadata) {
        Map<String, String> map = new LinkedHashMap<>();
        if (metadata == null) {
            return map;
        }

        String[] names = metadata.names();
        if (names == null) {
            return map;
        }

        int count = 0;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = metadata.get(name);
            if (value == null) {
                continue;
            }

            String trimmed = value.strip();
            if (trimmed.length() > MAX_METADATA_VALUE_LENGTH) {
                trimmed = trimmed.substring(0, MAX_METADATA_VALUE_LENGTH);
            }

            map.put(name, trimmed);
            count++;
            if (count >= MAX_METADATA_ENTRIES) {
                break;
            }
        }

        return map;
    }

    private static String normalizeMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "application/octet-stream";
        }

        String lower = mime.strip().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "image/jpg" -> "image/jpeg";
            case "application/x-zip-compressed" -> "application/zip";
            default -> lower;
        };
    }

    public record AnaliseTikaResponse(String tipoMimeDetectado, Map<String, String> metadados) {}
}
