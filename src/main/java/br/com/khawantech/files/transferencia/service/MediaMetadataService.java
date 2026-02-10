package br.com.khawantech.files.transferencia.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.github.kokorin.jaffree.ffprobe.Format;
import com.github.kokorin.jaffree.ffprobe.Stream;

import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMetadataService {

    private final MinioService minioService;
    private final TikaFileAnalysisService tikaFileAnalysisService;

    @Value("${ffmpeg.bin-dir:/usr/bin}")
    private String ffmpegBinDir;

    public MediaMetadataResult extrair(String caminhoMinio, String tipoMime) {
        if (caminhoMinio == null || caminhoMinio.isBlank()) {
            return new MediaMetadataResult(Map.of(), Map.of());
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("media_meta_", ".bin");
            try (InputStream inputStream = minioService.obterArquivo(caminhoMinio).inputStream()) {
                Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Map<String, String> metadadosTika = tikaFileAnalysisService.analisarArquivoCompleto(tempFile);
            Map<String, String> metadadosTecnicos = extrairTecnicos(tempFile, tipoMime);

            return new MediaMetadataResult(metadadosTika, metadadosTecnicos);
        } catch (Exception e) {
            log.debug("Falha ao extrair metadados completos: {}", e.getMessage());
            return new MediaMetadataResult(Map.of(), Map.of());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.debug("Falha ao remover arquivo temporário de metadata: {}", e.getMessage());
                }
            }
        }
    }

    private Map<String, String> extrairTecnicos(Path tempFile, String tipoMime) {
        if (tipoMime == null) {
            return Map.of();
        }

        String mime = tipoMime.trim().toLowerCase();
        if (mime.startsWith("image/")) {
            return extrairImagem(tempFile);
        }

        if (mime.startsWith("video/") || mime.startsWith("audio/") || FormatoVideo.isVideoLike(mime)) {
            return extrairMidiaViaFfprobe(tempFile);
        }

        return Map.of();
    }

    private Map<String, String> extrairImagem(Path tempFile) {
        Map<String, String> metadados = new LinkedHashMap<>();
        try (ImageInputStream iis = ImageIO.createImageInputStream(tempFile.toFile())) {
            if (iis == null) {
                return Map.of();
            }

            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            ImageReader reader = readers.hasNext() ? readers.next() : null;
            if (reader == null) {
                return Map.of();
            }

            reader.setInput(iis, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            metadados.put("width", String.valueOf(width));
            metadados.put("height", String.valueOf(height));
            String formatName = reader.getFormatName();
            if (formatName != null && !formatName.isBlank()) {
                metadados.put("format", formatName);
            }
            reader.dispose();
        } catch (Exception e) {
            log.debug("Falha ao extrair metadados de imagem: {}", e.getMessage());
            return Map.of();
        }

        return metadados;
    }

    private Map<String, String> extrairMidiaViaFfprobe(Path tempFile) {
        Map<String, String> metadados = new LinkedHashMap<>();
        try {
            FFprobeResult result = FFprobe.atPath(Path.of(ffmpegBinDir))
                .setShowStreams(true)
                .setShowFormat(true)
                .setInput(tempFile.toString())
                .execute();

            Format format = result.getFormat();
            if (format != null) {
                if (format.getDuration() != null) {
                    metadados.put("duration", format.getDuration().toString());
                }
                if (format.getBitRate() != null) {
                    metadados.put("bitrate", format.getBitRate().toString());
                }
                if (format.getFormatName() != null) {
                    metadados.put("format", format.getFormatName());
                }
            }

            java.util.List<Stream> streams = result.getStreams();
            if (streams == null || streams.isEmpty()) {
                return metadados;
            }

            Stream videoStream = streams.stream()
                .filter(stream -> "video".equalsIgnoreCase(String.valueOf(stream.getCodecType())))
                .findFirst()
                .orElse(null);

            Stream audioStream = streams.stream()
                .filter(stream -> "audio".equalsIgnoreCase(String.valueOf(stream.getCodecType())))
                .findFirst()
                .orElse(null);

            if (videoStream != null) {
                if (videoStream.getCodecName() != null) {
                    metadados.put("videoCodec", videoStream.getCodecName());
                }
                if (videoStream.getWidth() != null) {
                    metadados.put("width", videoStream.getWidth().toString());
                }
                if (videoStream.getHeight() != null) {
                    metadados.put("height", videoStream.getHeight().toString());
                }
                if (videoStream.getAvgFrameRate() != null) {
                    metadados.put("fps", videoStream.getAvgFrameRate().toString());
                }
                if (videoStream.getBitRate() != null) {
                    metadados.put("videoBitrate", videoStream.getBitRate().toString());
                }
                if (videoStream.getDuration() != null) {
                    metadados.put("videoDuration", videoStream.getDuration().toString());
                }
            }

            if (audioStream != null) {
                if (audioStream.getCodecName() != null) {
                    metadados.put("audioCodec", audioStream.getCodecName());
                }
                if (audioStream.getBitRate() != null) {
                    metadados.put("audioBitrate", audioStream.getBitRate().toString());
                }
                if (audioStream.getDuration() != null) {
                    metadados.put("audioDuration", audioStream.getDuration().toString());
                }
            }
        } catch (Exception e) {
            log.debug("Falha ao extrair metadados via ffprobe: {}", e.getMessage());
            return Map.of();
        }

        return metadados;
    }

    public record MediaMetadataResult(Map<String, String> metadadosTika, Map<String, String> metadadosTecnicos) {}
}
