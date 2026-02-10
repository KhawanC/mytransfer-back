package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ImageConversionEvent;
import br.com.khawantech.files.transferencia.service.ImageConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageConversionListener {

    private final ImageConversionService imageConversionService;

    @RabbitListener(queues = RabbitConfig.QUEUE_IMAGE_CONVERSION)
    public void onImageConversion(ImageConversionEvent event) {
        log.info("Evento de conversão de imagem recebido: {}", event);

        try {
            imageConversionService.processarConversao(event);
            log.info("Conversão de imagem concluída com sucesso: {}", event.getArquivoId());

        } catch (Exception e) {
            log.error("Erro ao processar conversão de imagem para arquivo {}: {}", 
                event.getArquivoId(), e.getMessage(), e);
            throw new ListenerExecutionFailedException(
                "Falha ao processar conversão de imagem", e
            );
        }
    }
}
