package br.com.khawantech.files.assinatura.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.assinatura.dto.AssinaturaStatusResponse;
import br.com.khawantech.files.assinatura.dto.CheckoutResponse;
import br.com.khawantech.files.assinatura.dto.PlanoAssinaturaResponse;
import br.com.khawantech.files.assinatura.entity.Assinatura;
import br.com.khawantech.files.assinatura.entity.PlanoAssinatura;
import br.com.khawantech.files.assinatura.entity.StatusAssinatura;
import br.com.khawantech.files.assinatura.repository.AssinaturaRepository;
import br.com.khawantech.files.assinatura.repository.PlanoAssinaturaRepository;
import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.entity.UserType;
import br.com.khawantech.files.user.service.UserService;
import br.com.khawantech.files.transferencia.dto.NotificacaoResponse;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import com.openpix.sdk.OpenSSL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssinaturaService {

    private final AssinaturaRepository repository;
    private final PlanoAssinaturaRepository planoRepository;
    private final WooviClient client;
    private final UserService userService;
    private final WebSocketNotificationService notificationService;

    public List<PlanoAssinaturaResponse> listarPlanos() {
        return planoRepository.findAll().stream()
            .map(plano -> PlanoAssinaturaResponse.builder()
                .id(plano.getId())
                .nome(plano.getNome())
                .precoCentavos(plano.getPrecoCentavos())
                .duracaoDias(plano.getDuracaoDias())
                .build())
            .toList();
    }

    @Transactional
    public CheckoutResponse criarCheckout(User user, String planoId) {
        PlanoAssinatura plano = getPlano(planoId)
            .orElseThrow(() -> new IllegalArgumentException("Plano inválido"));

        Optional<Assinatura> ultimaAssinatura = repository.findFirstByUsuarioIdOrderByCriadaEmDesc(user.getId());
        if (ultimaAssinatura.isPresent()) {
            Assinatura assinatura = ultimaAssinatura.get();
            if (assinatura.getStatus() == StatusAssinatura.PENDENTE
                && planoId.equals(assinatura.getPlanoId())
                && cobrancaAindaValida(assinatura)) {
                return toCheckoutResponse(assinatura);
            }

            if (assinatura.getStatus() == StatusAssinatura.PENDENTE
                && assinatura.getPagamentoExpiraEm() != null
                && assinatura.getPagamentoExpiraEm().isBefore(Instant.now())) {
                assinatura.setStatus(StatusAssinatura.EXPIRADA);
                repository.save(assinatura);
            }
        }

        String referencia = UUID.randomUUID().toString();

        Assinatura assinatura = Assinatura.builder()
            .usuarioId(user.getId())
            .planoId(plano.getId())
            .planoNome(plano.getNome())
            .referenciaExterna(referencia)
            .status(StatusAssinatura.PENDENTE)
            .build();

        assinatura.generateId();
        repository.save(assinatura);

        WooviClient.CobrancaResponse cobranca = client.criarCobranca(plano, referencia);
        assinatura.setCobrancaExternaId(cobranca.cobrancaId());
        assinatura.setBrCode(cobranca.brCode());
        assinatura.setQrCodeImageUrl(cobranca.qrCodeImageUrl());
        assinatura.setPagamentoLinkUrl(cobranca.paymentLinkUrl());
        assinatura.setPagamentoExpiraEm(cobranca.expiraEm());
        assinatura.setPagamentoCriadoEm(Instant.now());

        repository.save(assinatura);

        return toCheckoutResponse(assinatura);
    }

    public AssinaturaStatusResponse buscarStatus(String usuarioId) {
        Optional<Assinatura> assinatura = repository.findFirstByUsuarioIdOrderByCriadaEmDesc(usuarioId);
        if (assinatura.isEmpty()) {
            return AssinaturaStatusResponse.builder()
                .status(StatusAssinatura.EXPIRADA)
                .build();
        }
        return toStatusResponse(assinatura.get());
    }

    @Transactional
    public AssinaturaStatusResponse marcarCelebracao(String usuarioId) {
        Assinatura assinatura = repository.findFirstByUsuarioIdOrderByCriadaEmDesc(usuarioId)
            .orElseThrow(() -> new IllegalStateException("Assinatura não encontrada"));

        assinatura.setCelebracaoExibida(true);
        repository.save(assinatura);

        return toStatusResponse(assinatura);
    }

    @Transactional
    public AssinaturaStatusResponse cancelarAssinatura(String usuarioId) {
        Assinatura assinatura = repository.findFirstByUsuarioIdOrderByCriadaEmDesc(usuarioId)
            .orElseThrow(() -> new IllegalStateException("Assinatura não encontrada"));

        assinatura.setCancelarAoFinalPeriodo(true);
        repository.save(assinatura);

        return toStatusResponse(assinatura);
    }

    @Transactional
    public boolean processarWebhook(String assinaturaExternaId, String referenciaExterna, String evento, Map<String, Object> data) {
        Assinatura assinatura = null;

        if (assinaturaExternaId != null) {
            assinatura = repository.findByCobrancaExternaId(assinaturaExternaId).orElse(null);
            if (assinatura == null) {
                assinatura = repository.findByAssinaturaExternaId(assinaturaExternaId).orElse(null);
            }
        }

        if (assinatura == null && referenciaExterna != null) {
            assinatura = repository.findByReferenciaExterna(referenciaExterna).orElse(null);
        }

        if (assinatura == null) {
            log.warn("Webhook sem assinatura encontrada: assinaturaExternaId={}, referencia={}", assinaturaExternaId, referenciaExterna);
            return false;
        }

        StatusAssinatura statusAnterior = assinatura.getStatus();
        assinatura.setUltimoEvento(evento);

        if (assinaturaExternaId != null && assinatura.getCobrancaExternaId() == null) {
            assinatura.setCobrancaExternaId(assinaturaExternaId);
        }

        AtualizacaoPeriodo periodo = extrairPeriodo(data, assinatura.getPlanoId());
        if (periodo != null) {
            assinatura.setPeriodoInicio(periodo.inicio());
            assinatura.setPeriodoFim(periodo.fim());
        }

        if (evento != null) {
            String eventoLower = evento.toLowerCase();
            if (eventoLower.contains("charge_completed")
                || eventoLower.contains("movement_confirmed")
                || eventoLower.contains("transaction_received")
                || eventoLower.contains("paid")
                || eventoLower.contains("approved")) {
                assinatura.setStatus(StatusAssinatura.ATIVA);
                atualizarUsuarioPremium(assinatura.getUsuarioId());
            } else if (eventoLower.contains("charge_expired") || eventoLower.contains("expired")) {
                assinatura.setStatus(StatusAssinatura.EXPIRADA);
                atualizarUsuarioFree(assinatura.getUsuarioId());
            } else if (eventoLower.contains("movement_failed") || eventoLower.contains("fail") || eventoLower.contains("rejected")) {
                assinatura.setStatus(StatusAssinatura.FALHA);
                atualizarUsuarioFree(assinatura.getUsuarioId());
            } else if (eventoLower.contains("cancel")) {
                assinatura.setStatus(StatusAssinatura.CANCELADA);
            }
        }

        repository.save(assinatura);

        if (assinatura.getStatus() == StatusAssinatura.ATIVA
            && statusAnterior != StatusAssinatura.ATIVA
            && !assinatura.isCelebracaoExibida()) {
            notificarAssinaturaAtiva(assinatura);
        }

        return true;
    }

    @Transactional
    public int expirarAssinaturas() {
        List<Assinatura> expiradas = repository.findByStatusInAndPeriodoFimBefore(
            List.of(StatusAssinatura.ATIVA, StatusAssinatura.CANCELADA),
            Instant.now()
        );

        int atualizadas = 0;
        for (Assinatura assinatura : expiradas) {
            assinatura.setStatus(StatusAssinatura.EXPIRADA);
            repository.save(assinatura);
            atualizarUsuarioFree(assinatura.getUsuarioId());
            atualizadas++;
        }

        return atualizadas;
    }

    public boolean validarAssinatura(String payload, String assinaturaRecebida) {
        if (assinaturaRecebida == null || assinaturaRecebida.isBlank()) {
            return false;
        }
        return OpenSSL.verify(payload, assinaturaRecebida);
    }

    private Optional<PlanoAssinatura> getPlano(String planoId) {
        return planoRepository.findById(planoId);
    }

    private AssinaturaStatusResponse toStatusResponse(Assinatura assinatura) {
        return AssinaturaStatusResponse.builder()
            .assinaturaId(assinatura.getId())
            .planoId(assinatura.getPlanoId())
            .planoNome(assinatura.getPlanoNome())
            .status(assinatura.getStatus())
            .periodoInicio(assinatura.getPeriodoInicio())
            .periodoFim(assinatura.getPeriodoFim())
                .brCode(assinatura.getBrCode())
                .qrCodeImageUrl(assinatura.getQrCodeImageUrl())
                .paymentLinkUrl(assinatura.getPagamentoLinkUrl())
                .pagamentoExpiraEm(assinatura.getPagamentoExpiraEm())
            .cancelarAoFinalPeriodo(assinatura.isCancelarAoFinalPeriodo())
            .celebracaoExibida(assinatura.isCelebracaoExibida())
            .build();
    }

    private AtualizacaoPeriodo extrairPeriodo(Map<String, Object> data, String planoId) {
        if (data == null) return null;

        Instant inicio = parseInstant(data.get("current_period_start"));
        Instant fim = parseInstant(data.get("current_period_end"));

        if (fim == null) {
            Instant expiresAt = parseInstant(data.get("expires_at"));
            if (expiresAt != null) {
                fim = expiresAt;
            }
        }

        if (inicio == null && fim == null && planoId != null) {
            Optional<PlanoAssinatura> plano = getPlano(planoId);
            if (plano.isPresent()) {
                inicio = Instant.now();
                fim = inicio.plus(plano.get().getDuracaoDias(), ChronoUnit.DAYS);
            }
        }

        if (inicio == null && fim != null) {
            inicio = fim.minus(30, ChronoUnit.DAYS);
        }

        if (inicio == null && fim == null) return null;

        return new AtualizacaoPeriodo(inicio, fim);
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (epoch > 100000000000L) {
                return Instant.ofEpochMilli(epoch);
            }
            return Instant.ofEpochSecond(epoch);
        }
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (Exception ignored) {
                try {
                    long epoch = Long.parseLong(text);
                    if (epoch > 100000000000L) {
                        return Instant.ofEpochMilli(epoch);
                    }
                    return Instant.ofEpochSecond(epoch);
                } catch (Exception ignored2) {
                    return null;
                }
            }
        }
        return null;
    }

    private void atualizarUsuarioPremium(String usuarioId) {
        userService.findById(usuarioId).ifPresent(usuario -> {
            if (usuario.getUserType() != UserType.PREMIUM) {
                usuario.setUserType(UserType.PREMIUM);
                userService.update(usuario);
            }
        });
    }

    private void atualizarUsuarioFree(String usuarioId) {
        userService.findById(usuarioId).ifPresent(usuario -> {
            if (usuario.getUserType() != UserType.FREE) {
                usuario.setUserType(UserType.FREE);
                userService.update(usuario);
            }
        });
    }

    private void notificarAssinaturaAtiva(Assinatura assinatura) {
        NotificacaoResponse notificacao = NotificacaoResponse.builder()
            .tipo(NotificacaoResponse.TipoNotificacao.ASSINATURA_PAGA)
            .sessaoId("")
            .mensagem("Assinatura ativada")
            .dados(assinatura.getId())
            .timestamp(Instant.now())
            .build();

        notificationService.notificarUsuario(assinatura.getUsuarioId(), notificacao);
    }

    private boolean cobrancaAindaValida(Assinatura assinatura) {
        boolean hasQrData = (assinatura.getBrCode() != null && !assinatura.getBrCode().isBlank())
            || (assinatura.getQrCodeImageUrl() != null && !assinatura.getQrCodeImageUrl().isBlank());
        return hasQrData
            && assinatura.getPagamentoExpiraEm() != null
            && assinatura.getPagamentoExpiraEm().isAfter(Instant.now());
    }

    private CheckoutResponse toCheckoutResponse(Assinatura assinatura) {
        return CheckoutResponse.builder()
            .assinaturaId(assinatura.getId())
            .planoId(assinatura.getPlanoId())
            .brCode(assinatura.getBrCode())
            .qrCodeImageUrl(assinatura.getQrCodeImageUrl())
            .paymentLinkUrl(assinatura.getPagamentoLinkUrl())
            .expiraEm(assinatura.getPagamentoExpiraEm())
            .build();
    }

    private record AtualizacaoPeriodo(Instant inicio, Instant fim) {}
}
