package br.com.khawantech.files.transferencia.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static br.com.khawantech.files.transferencia.config.RabbitConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListener {

    @RabbitListener(queues = QUEUE_CHUNK_DLQ)
    public void handleChunkDlqMessage(Message message) {
        logDlqMessage("CHUNK", message);
    }

    @RabbitListener(queues = QUEUE_ARQUIVO_DLQ)
    public void handleArquivoDlqMessage(Message message) {
        logDlqMessage("ARQUIVO", message);
    }

    @RabbitListener(queues = QUEUE_SESSAO_DLQ)
    public void handleSessaoDlqMessage(Message message) {
        logDlqMessage("SESSAO", message);
    }

    @RabbitListener(queues = QUEUE_ASSINATURA_WEBHOOK_DLQ)
    public void handleAssinaturaWebhookDlqMessage(Message message) {
        logDlqMessage("ASSINATURA_WEBHOOK", message);
    }

    private void logDlqMessage(String type, Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        
        String reason = (String) headers.get("x-first-death-reason");
        String exchange = (String) headers.get("x-first-death-exchange");
        String queue = (String) headers.get("x-first-death-queue");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> xDeathHeader = (List<Map<String, Object>>) headers.get("x-death");
        Long deathCount = 0L;
        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            deathCount = (Long) xDeathHeader.get(0).get("count");
        }

        log.error("""
            ============================================
            MENSAGEM NA DLQ - {}
            ============================================
            Motivo: {}
            Exchange Original: {}
            Fila Original: {}
            Tentativas: {}
            Corpo da Mensagem: {}
            Headers Completos: {}
            ============================================
            """, 
            type, 
            reason, 
            exchange, 
            queue, 
            deathCount,
            body,
            headers
        );
    }
}
