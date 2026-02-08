package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ArquivoCompletoEvent;
import br.com.khawantech.files.transferencia.service.MinioService;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArquivoCompletoListener {

    private final WebSocketNotificationService notificationService;
    private final MinioService minioService;

    @RabbitListener(queues = RabbitConfig.QUEUE_ARQUIVO_COMPLETO)
    public void handleArquivoCompleto(ArquivoCompletoEvent event) {
        log.info("Arquivo completo recebido: {} - {}", event.getArquivoId(), event.getNomeOriginal());

        try {
            String urlDownload = minioService.gerarUrlDownload(event.getCaminhoMinio(), 60);

            notificationService.notificarArquivoDisponivel(
                event.getSessaoId(),
                event.getArquivoId(),
                event.getNomeOriginal(),
                urlDownload
            );

            log.info("Notificação de arquivo disponível enviada: {}", event.getArquivoId());

        } catch (Exception e) {
            log.error("Erro ao processar evento de arquivo completo: {}", e.getMessage());
            notificationService.notificarErroUpload(
                event.getSessaoId(),
                event.getArquivoId(),
                "Erro ao processar arquivo: " + e.getMessage()
            );
        }
    }
}
