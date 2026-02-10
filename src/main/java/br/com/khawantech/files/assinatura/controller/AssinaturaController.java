package br.com.khawantech.files.assinatura.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.khawantech.files.assinatura.config.WooviProperties;
import br.com.khawantech.files.assinatura.dto.AssinaturaStatusResponse;
import br.com.khawantech.files.assinatura.dto.CheckoutRequest;
import br.com.khawantech.files.assinatura.dto.CheckoutResponse;
import br.com.khawantech.files.assinatura.dto.PlanoAssinaturaResponse;
import br.com.khawantech.files.assinatura.dto.WebhookResponse;
import br.com.khawantech.files.assinatura.service.AssinaturaService;
import br.com.khawantech.files.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/assinaturas")
@RequiredArgsConstructor
public class AssinaturaController {

    private final AssinaturaService assinaturaService;
    private final WooviProperties properties;
    private final ObjectMapper objectMapper;

    @GetMapping("/planos")
    public ResponseEntity<List<PlanoAssinaturaResponse>> listarPlanos() {
        return ResponseEntity.ok(assinaturaService.listarPlanos());
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> criarCheckout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal User user) {
        CheckoutResponse response = assinaturaService.criarCheckout(user, request.getPlanoId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/status")
    public ResponseEntity<AssinaturaStatusResponse> obterStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(assinaturaService.buscarStatus(user.getId()));
    }

    @PostMapping("/celebration")
    public ResponseEntity<AssinaturaStatusResponse> marcarCelebracao(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(assinaturaService.marcarCelebracao(user.getId()));
    }

    @PostMapping("/cancelar")
    public ResponseEntity<AssinaturaStatusResponse> cancelar(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(assinaturaService.cancelarAssinatura(user.getId()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<WebhookResponse> webhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {
        String assinatura = headers.getOrDefault(properties.getWebhookSignatureHeader(), "");

        if (!assinaturaService.validarAssinatura(payload, assinatura)) {
            log.warn("Assinatura inválida no webhook da Woovi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new WebhookResponse(false));
        }

        try {
            Map<String, Object> body = objectMapper.readValue(payload, Map.class);
            String evento = firstString(body, "event", "type", "event_type", "status");
            Map<String, Object> data = extractData(body);

            String cobrancaExternaId = firstString(data, "paymentLinkID", "paymentLinkId", "chargeId", "transactionID", "id", "globalID");
            String referencia = firstString(data, "correlationID", "correlationId", "reference", "reference_id", "external_reference");
            if (referencia == null) {
                referencia = firstString(body, "correlationID", "correlationId", "reference", "reference_id", "external_reference");
            }

            boolean ok = assinaturaService.processarWebhook(cobrancaExternaId, referencia, evento, data);
            return ResponseEntity.ok(new WebhookResponse(ok));
        } catch (Exception e) {
            log.error("Erro ao processar webhook da Woovi", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new WebhookResponse(false));
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
