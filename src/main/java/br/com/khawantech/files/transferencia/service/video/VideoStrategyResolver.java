package br.com.khawantech.files.transferencia.service.video;

import br.com.khawantech.files.transferencia.dto.FormatoVideo;
import br.com.khawantech.files.transferencia.exception.ConversaoNaoSuportadaException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class VideoStrategyResolver {

    private final Map<FormatoVideo, VideoConversionStrategy> strategies;

    public VideoStrategyResolver() {
        this.strategies = Map.of(
            FormatoVideo.MP4, new DefaultVideoConversionStrategy(
                FormatoVideo.MP4,
                new VideoConversionProfile("mp4", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart"
                ))
            ),
            FormatoVideo.MKV, new DefaultVideoConversionStrategy(
                FormatoVideo.MKV,
                new VideoConversionProfile("matroska", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k"
                ))
            ),
            FormatoVideo.WEBM, new DefaultVideoConversionStrategy(
                FormatoVideo.WEBM,
                new VideoConversionProfile("webm", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libvpx-vp9",
                    "-b:v", "0",
                    "-crf", "33",
                    "-c:a", "libopus",
                    "-b:a", "96k"
                ))
            ),
            FormatoVideo.MOV, new DefaultVideoConversionStrategy(
                FormatoVideo.MOV,
                new VideoConversionProfile("mov", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k"
                ))
            ),
            FormatoVideo.AVI, new DefaultVideoConversionStrategy(
                FormatoVideo.AVI,
                new VideoConversionProfile("avi", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "mpeg4",
                    "-q:v", "5",
                    "-c:a", "libmp3lame",
                    "-b:a", "128k"
                ))
            ),
            FormatoVideo.FLV, new DefaultVideoConversionStrategy(
                FormatoVideo.FLV,
                new VideoConversionProfile("flv", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k"
                ))
            ),
            FormatoVideo.THREE_GP, new DefaultVideoConversionStrategy(
                FormatoVideo.THREE_GP,
                new VideoConversionProfile("3gp", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "libx264",
                    "-profile:v", "baseline",
                    "-level", "3.0",
                    "-pix_fmt", "yuv420p",
                    "-preset", "veryfast",
                    "-crf", "28",
                    "-c:a", "aac",
                    "-b:a", "96k",
                    "-ar", "44100"
                ))
            ),
            FormatoVideo.MPEG, new DefaultVideoConversionStrategy(
                FormatoVideo.MPEG,
                new VideoConversionProfile("mpeg", List.of(
                    "-map", "0:v:0?",
                    "-map", "0:a:0?",
                    "-c:v", "mpeg2video",
                    "-q:v", "5",
                    "-c:a", "mp2",
                    "-b:a", "192k"
                ))
            ),
            FormatoVideo.GIF, new DefaultVideoConversionStrategy(
                FormatoVideo.GIF,
                new VideoConversionProfile("gif", List.of(
                    "-map", "0:v:0?",
                    "-vf", "fps=12,scale=480:-1:flags=lanczos",
                    "-loop", "0"
                ))
            )
        );
    }

    public VideoConversionStrategy resolve(FormatoVideo target) {
        VideoConversionStrategy strategy = strategies.get(target);
        if (strategy == null) {
            throw new ConversaoNaoSuportadaException("Formato de conversão não suportado");
        }
        return strategy;
    }
}
