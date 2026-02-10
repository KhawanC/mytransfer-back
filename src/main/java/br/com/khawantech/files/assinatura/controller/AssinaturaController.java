package br.com.khawantech.files.assinatura.controller;

import java.util.List;
import java.time.Instant;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.khawantech.files.assinatura.config.WooviProperties;
import br.com.khawantech.files.assinatura.dto.AssinaturaStatusResponse;
import br.com.khawantech.files.assinatura.dto.CheckoutRequest;
import br.com.khawantech.files.assinatura.dto.CheckoutResponse;
import br.com.khawantech.files.assinatura.dto.PlanoAssinaturaResponse;
import br.com.khawantech.files.assinatura.dto.WebhookAssinaturaEvent;
import br.com.khawantech.files.assinatura.dto.WebhookResponse;
import br.com.khawantech.files.assinatura.service.AssinaturaService;
import br.com.khawantech.files.transferencia.config.RabbitConfig;
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
    private final RabbitTemplate rabbitTemplate;

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
        try {
            WebhookAssinaturaEvent event = WebhookAssinaturaEvent.builder()
                .payload(payload)
                .signature(assinatura)
                .receivedAt(Instant.now())
                .build();

            rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TRANSFERENCIA,
                RabbitConfig.ROUTING_KEY_ASSINATURA_WEBHOOK,
                event
            );
        } catch (Exception e) {
            log.error("Erro ao processar webhook da Woovi", e);
        }

        return ResponseEntity.ok(new WebhookResponse(true));
    }
}
