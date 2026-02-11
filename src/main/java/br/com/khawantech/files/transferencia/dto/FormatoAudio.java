package br.com.khawantech.files.transferencia.dto;

import java.util.List;
import java.util.Optional;

public enum FormatoAudio {
    MP3("audio/mpeg", "mp3", "libmp3lame"),
    WAV("audio/wav", "wav", "pcm_s16le"),
    AAC("audio/aac", "aac", "aac"),
    M4A("audio/mp4", "m4a", "aac"),
    OGG("audio/ogg", "ogg", "libvorbis"),
    FLAC("audio/flac", "flac", "flac"),
    OPUS("audio/opus", "opus", "libopus"),
    WEBM("audio/webm", "webm", "libopus"),
    AIFF("audio/aiff", "aiff", "pcm_s16be"),
    AIF("audio/x-aiff", "aif", "pcm_s16be"),
    ALAC("audio/alac", "m4a", "alac"),
    WMA("audio/x-ms-wma", "wma", "wmav2"),
    AMR("audio/amr", "amr", "libopencore_amrnb"),
    AU("audio/basic", "au", "pcm_s16be"),
    SND("audio/x-au", "snd", "pcm_s16be");

    private final String mimeType;
    private final String extension;
    private final String ffmpegCodec;

    FormatoAudio(String mimeType, String extension, String ffmpegCodec) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.ffmpegCodec = ffmpegCodec;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String getFfmpegCodec() {
        return ffmpegCodec;
    }

    public static Optional<FormatoAudio> fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.trim().toUpperCase();
        try {
            return Optional.of(FormatoAudio.valueOf(v));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<FormatoAudio> fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }
        String normalized = mimeType.strip().toLowerCase();
        int paramIdx = normalized.indexOf(';');
        if (paramIdx > 0) {
            normalized = normalized.substring(0, paramIdx).strip();
        }

        for (FormatoAudio formato : values()) {
            if (formato.mimeType.equalsIgnoreCase(normalized)) {
                return Optional.of(formato);
            }
        }

        if ("audio/x-wav".equals(normalized) || "audio/wave".equals(normalized)) {
            return Optional.of(WAV);
        }
        if ("audio/x-m4a".equals(normalized)) {
            return Optional.of(M4A);
        }
        if ("audio/x-flac".equals(normalized)) {
            return Optional.of(FLAC);
        }
        if ("audio/mp3".equals(normalized)) {
            return Optional.of(MP3);
        }
        if ("audio/vorbis".equals(normalized)) {
            return Optional.of(OGG);
        }
        if ("audio/x-alac".equals(normalized)) {
            return Optional.of(ALAC);
        }

        return Optional.empty();
    }

    public static Optional<FormatoAudio> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        String ext = extension.toLowerCase().replace(".", "");
        for (FormatoAudio formato : values()) {
            if (formato.extension.equalsIgnoreCase(ext)) {
                return Optional.of(formato);
            }
        }
        return Optional.empty();
    }

    public static boolean isAudioConversivel(String mimeType) {
        return fromMimeType(mimeType).isPresent();
    }

    public static List<FormatoAudio> getFormatosDisponiveis(String mimeTypeAtual) {
        Optional<FormatoAudio> atual = fromMimeType(mimeTypeAtual);
        return List.of(values()).stream()
            .filter(f -> atual.isEmpty() || f != atual.get())
            .toList();
    }
}
