package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.AudioConversionEvent;
import br.com.khawantech.files.transferencia.service.AudioConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioConversionListener {

    private final AudioConversionService audioConversionService;

    @RabbitListener(queues = RabbitConfig.QUEUE_AUDIO_CONVERSION)
    public void onAudioConversion(AudioConversionEvent event) {
        log.info("Evento de conversão de áudio recebido: {}", event);

        try {
            audioConversionService.processarConversao(event);
            log.info("Conversão de áudio concluída com sucesso: {}", event.getArquivoId());

        } catch (Exception e) {
            log.error("Erro ao processar conversão de áudio para arquivo {}: {}", 
                event.getArquivoId(), e.getMessage(), e);
            throw new ListenerExecutionFailedException(
                "Falha ao processar conversão de áudio", e
            );
        }
    }
}
