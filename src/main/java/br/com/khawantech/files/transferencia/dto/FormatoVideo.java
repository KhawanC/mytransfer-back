package br.com.khawantech.files.transferencia.dto;

import java.util.List;
import java.util.Optional;

public enum FormatoVideo {
    MP4("video/mp4", "mp4", "mp4"),
    MKV("video/x-matroska", "mkv", "matroska"),
    WEBM("video/webm", "webm", "webm"),
    MOV("video/quicktime", "mov", "mov"),
    AVI("video/x-msvideo", "avi", "avi"),
    FLV("video/x-flv", "flv", "flv"),
    THREE_GP("video/3gpp", "3gp", "3gp"),
    MPEG("video/mpeg", "mpeg", "mpeg"),
    GIF("image/gif", "gif", "gif");

    private final String mimeType;
    private final String extension;
    private final String ffmpegFormat;

    FormatoVideo(String mimeType, String extension, String ffmpegFormat) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.ffmpegFormat = ffmpegFormat;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String getFfmpegFormat() {
        return ffmpegFormat;
    }

    public String apiValue() {
        if (this == THREE_GP) {
            return "3GP";
        }
        return name();
    }

    public static Optional<FormatoVideo> fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.trim().toUpperCase();
        if ("3GP".equals(v)) {
            return Optional.of(THREE_GP);
        }
        try {
            return Optional.of(FormatoVideo.valueOf(v));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<FormatoVideo> fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }
        String normalized = mimeType.strip().toLowerCase();
        int paramIdx = normalized.indexOf(';');
        if (paramIdx > 0) {
            normalized = normalized.substring(0, paramIdx).strip();
        }

        for (FormatoVideo formato : values()) {
            if (formato.mimeType.equalsIgnoreCase(normalized)) {
                return Optional.of(formato);
            }
        }

        if ("video/x-matroska".equals(normalized) || "video/matroska".equals(normalized)) {
            return Optional.of(MKV);
        }
        if ("video/avi".equals(normalized)) {
            return Optional.of(AVI);
        }
        if ("video/3gpp2".equals(normalized)) {
            return Optional.of(THREE_GP);
        }
        return Optional.empty();
    }

    public static boolean isVideoLike(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.strip().toLowerCase();
        int paramIdx = normalized.indexOf(';');
        if (paramIdx > 0) {
            normalized = normalized.substring(0, paramIdx).strip();
        }
        return normalized.startsWith("video/") || "image/gif".equals(normalized);
    }

    public static List<FormatoVideo> getFormatosDisponiveis(String mimeTypeAtual) {
        Optional<FormatoVideo> atual = fromMimeType(mimeTypeAtual);
        return List.of(values()).stream()
            .filter(f -> atual.isEmpty() || f != atual.get())
            .toList();
    }
}
