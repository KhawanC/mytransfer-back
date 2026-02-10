package br.com.khawantech.files.assinatura.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;

import br.com.khawantech.files.assinatura.config.WooviProperties;
import br.com.khawantech.files.assinatura.entity.PlanoAssinatura;
import com.openpix.sdk.Charge;
import com.openpix.sdk.ChargeBuilder;
import com.openpix.sdk.ChargeResponse;
import com.openpix.sdk.ChargeType;
import com.openpix.sdk.WooviSDK;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WooviClient {

    private final WooviProperties properties;

    public CobrancaResponse criarCobranca(PlanoAssinatura plano, String referencia) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Woovi API key nao configurada");
        }

        WooviSDK sdk = new WooviSDK(apiKey).configureIgnoreUnknownKeysJson(true);

        Instant expiraEm = Instant.now().plus(10, ChronoUnit.MINUTES);

        ChargeBuilder builder = new ChargeBuilder()
            .correlationID(referencia)
            .value(plano.getPrecoCentavos())
            .comment(plano.getNome())
            .type(ChargeType.DYNAMIC)
            .expiresDate(expiraEm.toString())
            .additionalInfo("planoId", plano.getId())
            .additionalInfo("planoNome", plano.getNome());

        try {
            ChargeResponse response = sdk.createChargeAsync(builder).get();
            Charge charge = response.getCharge();
            String brCode = charge.getBrCode();
            if (brCode == null || brCode.isBlank()) {
                brCode = response.getBrCode();
            }
            Instant expiraEmCalculado = parseInstant(charge.getExpiresDate(), expiraEm);
            return new CobrancaResponse(
                charge.getPaymentLinkID(),
                brCode,
                charge.getQrCodeImage(),
                charge.getPaymentLinkUrl(),
                expiraEmCalculado
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha ao criar cobranca na Woovi", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha ao criar cobranca na Woovi", e.getCause());
        }
    }

    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record CobrancaResponse(
        String cobrancaId,
        String brCode,
        String qrCodeImageUrl,
        String paymentLinkUrl,
        Instant expiraEm
    ) {}
}
