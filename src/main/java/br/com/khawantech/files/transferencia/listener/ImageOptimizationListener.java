package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ImageOptimizationEvent;
import br.com.khawantech.files.transferencia.service.ImageOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageOptimizationListener {

    private final ImageOptimizationService imageOptimizationService;

    @RabbitListener(queues = RabbitConfig.QUEUE_IMAGE_OPTIMIZATION)
    public void onImageOptimization(ImageOptimizationEvent event) {
        log.info("Evento de otimização de imagem recebido: {}", event);

        try {
            imageOptimizationService.processarOtimizacao(event);
            log.info("Otimização de imagem concluída com sucesso: {}", event.getArquivoId());

        } catch (Exception e) {
            log.error("Erro ao processar otimização de imagem para arquivo {}: {}",
                event.getArquivoId(), e.getMessage(), e);
            throw new ListenerExecutionFailedException(
                "Falha ao processar otimização de imagem", e
            );
        }
    }
}
