package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.VideoOptimizationEvent;
import br.com.khawantech.files.transferencia.service.VideoOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoOptimizationListener {

    private final VideoOptimizationService videoOptimizationService;

    @RabbitListener(queues = RabbitConfig.QUEUE_VIDEO_OPTIMIZATION)
    public void onVideoOptimization(VideoOptimizationEvent event) {
        log.info("Evento de otimização de vídeo recebido: {}", event);
        videoOptimizationService.processarOtimizacao(event);
    }
}
