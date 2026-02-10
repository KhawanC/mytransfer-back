package br.com.khawantech.files.assinatura.listener;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.khawantech.files.assinatura.dto.WebhookAssinaturaEvent;
import br.com.khawantech.files.assinatura.service.AssinaturaService;
import br.com.khawantech.files.transferencia.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookAssinaturaListener {

    private final AssinaturaService assinaturaService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.QUEUE_ASSINATURA_WEBHOOK)
    public void onWebhook(WebhookAssinaturaEvent event) throws Exception {
        if (event == null || event.getPayload() == null) {
            return;
        }

        String assinatura = event.getSignature();
        if (assinatura == null || assinatura.isBlank()) {
            assinatura = "";
        }

        if (!assinaturaService.validarAssinatura(event.getPayload(), assinatura)) {
            log.warn("Assinatura invalida no webhook da Woovi");
            return;
        }

        Map<String, Object> body = objectMapper.readValue(event.getPayload(), Map.class);
        String evento = firstString(body, "event", "type", "event_type", "status");
        Map<String, Object> data = extractData(body);

        String cobrancaExternaId = firstString(data, "paymentLinkID", "paymentLinkId", "chargeId", "transactionID", "id", "globalID");
        String referencia = firstString(data, "correlationID", "correlationId", "reference", "reference_id", "external_reference");
        if (referencia == null) {
            referencia = firstString(body, "correlationID", "correlationId", "reference", "reference_id", "external_reference");
        }

        boolean ok = assinaturaService.processarWebhook(cobrancaExternaId, referencia, evento, data);
        if (!ok) {
            log.warn("Webhook de assinatura nao processado: referencia={}", referencia);
        }
    }

    private Map<String, Object> extractData(Map<String, Object> body) {
        Map<String, Object> data = asMap(body.get("data"));
        Map<String, Object> charge = asMap(body.get("charge"));
        Map<String, Object> transaction = asMap(body.get("transaction"));
        Map<String, Object> chargeFromTransaction = transaction != null ? asMap(transaction.get("charge")) : null;
        Map<String, Object> chargeFromData = data != null ? asMap(data.get("charge")) : null;

        if (charge != null) return charge;
        if (chargeFromData != null) return chargeFromData;
        if (chargeFromTransaction != null) return chargeFromTransaction;
        if (data != null) return data;
        return body;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }
}
