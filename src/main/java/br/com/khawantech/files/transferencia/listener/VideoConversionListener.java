package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.VideoConversionEvent;
import br.com.khawantech.files.transferencia.service.VideoConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoConversionListener {

    private final VideoConversionService videoConversionService;

    @RabbitListener(queues = RabbitConfig.QUEUE_VIDEO_CONVERSION)
    public void onVideoConversion(VideoConversionEvent event) {
        log.info("Evento de conversão de vídeo recebido: {}", event);
        videoConversionService.processarConversao(event);
    }
}
