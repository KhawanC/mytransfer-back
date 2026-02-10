package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.SessaoAtualizadaEvent;
import br.com.khawantech.files.transferencia.entity.StatusSessao;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessaoAtualizadaListener {

    private final WebSocketNotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.QUEUE_SESSAO_ATUALIZADA)
    public void handleSessaoAtualizada(SessaoAtualizadaEvent event) {
        log.info("Sessão atualizada: {} - {} -> {}",
            event.getSessaoId(), event.getStatusAnterior(), event.getStatusNovo());


        if (event.getStatusNovo() == StatusSessao.AGUARDANDO_APROVACAO &&
            event.getStatusAnterior() == StatusSessao.AGUARDANDO) {
            notificationService.notificarSolicitacaoEntrada(
                event.getSessaoId(),
                event.getUsuarioConvidadoPendenteId(),
                event.getNomeUsuarioConvidadoPendente()
            );
        }
        if (event.getStatusNovo() == StatusSessao.ATIVA &&
            event.getStatusAnterior() == StatusSessao.AGUARDANDO_APROVACAO) {
            notificationService.notificarEntradaAprovada(event.getSessaoId(), event.getUsuarioConvidadoId());
            notificationService.notificarUsuarioEntrou(event.getSessaoId(), event.getUsuarioConvidadoId());
        }

        if (event.getStatusNovo() == StatusSessao.ENCERRADA) {
            notificationService.notificarSessaoEncerrada(event.getSessaoId(), event.getMotivo());
        }

        if (event.getStatusNovo() == StatusSessao.EXPIRADA) {
            notificationService.notificarSessaoExpirada(event.getSessaoId());
        }
    }
}
