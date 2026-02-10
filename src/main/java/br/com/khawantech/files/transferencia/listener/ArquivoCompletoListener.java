package br.com.khawantech.files.transferencia.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ArquivoCompletoEvent;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.service.DownloadTokenService;
import br.com.khawantech.files.transferencia.service.ImageConversionService;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArquivoCompletoListener {

    private final WebSocketNotificationService notificationService;
    private final DownloadTokenService downloadTokenService;
    private final ImageConversionService imageConversionService;
    private final ArquivoRepository arquivoRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @RabbitListener(queues = RabbitConfig.QUEUE_ARQUIVO_COMPLETO)
    public void handleArquivoCompleto(ArquivoCompletoEvent event) {
        log.info("Arquivo completo recebido: {} - {}", event.getArquivoId(), event.getNomeOriginal());

        try {
            detectarImagemConversivel(event);

            String token = downloadTokenService.gerarToken(event.getArquivoId(), event.getRemetenteId());
            String urlDownload = baseUrl + "/api/files/d/" + token;

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

    private void detectarImagemConversivel(ArquivoCompletoEvent event) {
        try {
            if (imageConversionService.isImagemConversivel(event.getTipoMime())) {
                arquivoRepository.findById(event.getArquivoId()).ifPresent(arquivo -> {
                    arquivo.setConversivel(true);
                    arquivoRepository.save(arquivo);
                    log.info("Arquivo {} marcado como conversível", event.getArquivoId());
                });
            }
        } catch (Exception e) {
            log.warn("Erro ao detectar imagem conversível para arquivo {}: {}", 
                event.getArquivoId(), e.getMessage());
        }
    }
}
