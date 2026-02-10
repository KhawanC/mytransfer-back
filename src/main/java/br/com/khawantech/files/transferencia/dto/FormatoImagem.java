package br.com.khawantech.files.transferencia.dto;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public enum FormatoImagem {
    JPEG("image/jpeg", "jpg"),
    JPG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    BMP("image/bmp", "bmp"),
    WEBP("image/webp", "webp"),
    SVG("image/svg+xml", "svg"),
    TIFF("image/tiff", "tiff"),
    ICO("image/x-icon", "ico");

    private final String mimeType;
    private final String extension;

    FormatoImagem(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static Optional<FormatoImagem> fromMimeType(String mimeType) {
        return Arrays.stream(values())
            .filter(formato -> formato.mimeType.equalsIgnoreCase(mimeType))
            .findFirst();
    }

    public static Optional<FormatoImagem> fromExtension(String extension) {
        String ext = extension.toLowerCase().replace(".", "");
        return Arrays.stream(values())
            .filter(formato -> formato.extension.equalsIgnoreCase(ext))
            .findFirst();
    }

    public static boolean isImagemConversivel(String mimeType) {
        return fromMimeType(mimeType).isPresent();
    }

    public static List<FormatoImagem> getFormatosDisponiveis(String mimeTypeAtual) {
        return Arrays.stream(values())
            .filter(formato -> !formato.mimeType.equalsIgnoreCase(mimeTypeAtual))
            .distinct()
            .toList();
    }
}
