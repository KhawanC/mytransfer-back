package br.com.khawantech.files.transferencia.listener;

import java.time.Instant;
import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ArquivoCompletoEvent;
import br.com.khawantech.files.transferencia.dto.ArquivoSecurityEvent;
import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.ChunkArquivo;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;
import br.com.khawantech.files.transferencia.repository.ArquivoRepository;
import br.com.khawantech.files.transferencia.repository.ChunkArquivoRepository;
import br.com.khawantech.files.transferencia.service.ArquivoRedisService;
import br.com.khawantech.files.transferencia.service.ArquivoSecurityPolicyService;
import br.com.khawantech.files.transferencia.service.MediaMetadataService;
import br.com.khawantech.files.transferencia.service.MinioService;
import br.com.khawantech.files.transferencia.service.ProgressoUploadRedisService;
import br.com.khawantech.files.transferencia.service.SessaoService;
import br.com.khawantech.files.transferencia.service.TikaFileAnalysisService;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArquivoSecurityListener {

    private static final int MAX_PREFIX_BYTES = 64 * 1024;

    private final ArquivoRepository arquivoRepository;
    private final ChunkArquivoRepository chunkArquivoRepository;
    private final MinioService minioService;
    private final TikaFileAnalysisService tikaFileAnalysisService;
    private final ArquivoSecurityPolicyService securityPolicyService;
    private final WebSocketNotificationService notificationService;
    private final ArquivoRedisService arquivoRedisService;
    private final ProgressoUploadRedisService progressoRedisService;
    private final SessaoService sessaoService;
    private final MediaMetadataService mediaMetadataService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_ARQUIVO_SECURITY)
    @Transactional
    public void handleArquivoSecurity(ArquivoSecurityEvent event) {
        log.info("Análise de segurança recebida: arquivo={} sessao={}", event.getArquivoId(), event.getSessaoId());

        Arquivo arquivo = arquivoRepository.findById(event.getArquivoId())
            .orElseThrow(() -> new RuntimeException("Arquivo não encontrado: " + event.getArquivoId()));

        if (arquivo.getStatus() == StatusArquivo.COMPLETO || arquivo.getStatus() == StatusArquivo.BLOQUEADO || arquivo.getStatus() == StatusArquivo.ERRO) {
            return;
        }

        List<ChunkArquivo> chunks = chunkArquivoRepository.findByArquivoIdOrderByNumeroChunkAsc(arquivo.getId());
        if (chunks.size() < arquivo.getTotalChunks()) {
            log.warn("Chunks incompletos para análise: arquivo={} recebidos={} total={}", arquivo.getId(), chunks.size(), arquivo.getTotalChunks());
            return;
        }

        try {
            byte[] prefixo = minioService.lerPrefixoDeChunks(arquivo.getSessaoId(), arquivo.getId(), arquivo.getTotalChunks(), MAX_PREFIX_BYTES);
            TikaFileAnalysisService.AnaliseTikaResponse analise = tikaFileAnalysisService.analisar(prefixo);

            arquivo.setTipoMimeDetectado(analise.tipoMimeDetectado());
            arquivo.setMetadadosTika(analise.metadados());

            ArquivoSecurityPolicyService.Decision decision = securityPolicyService.avaliar(arquivo.getTipoMimeInformado(), analise.tipoMimeDetectado(), analise.metadados());
            if (!decision.permitido()) {
                bloquearArquivo(arquivo, decision.motivo());
                return;
            }

            String caminhoFinal = minioService.mergeChunks(
                arquivo.getSessaoId(),
                arquivo.getId(),
                arquivo.getNomeOriginal(),
                arquivo.getTotalChunks(),
                analise.tipoMimeDetectado()
            );

            MediaMetadataService.MediaMetadataResult metadadosCompletos = mediaMetadataService.extrair(caminhoFinal, analise.tipoMimeDetectado());
            if (!metadadosCompletos.metadadosTika().isEmpty()) {
                arquivo.setMetadadosTika(metadadosCompletos.metadadosTika());
            }
            if (!metadadosCompletos.metadadosTecnicos().isEmpty()) {
                arquivo.setMetadadosTecnicos(metadadosCompletos.metadadosTecnicos());
            }

            arquivo.setCaminhoMinio(caminhoFinal);
            arquivo.setTipoMime(analise.tipoMimeDetectado());
            arquivo.setStatus(StatusArquivo.COMPLETO);
            arquivo.setProgressoUpload(100.0);
            arquivo.setChunksRecebidos(arquivo.getTotalChunks());
            arquivo.setAtualizadoEm(Instant.now());

            arquivoRepository.save(arquivo);
            arquivoRedisService.atualizarArquivo(arquivo);

            sessaoService.incrementarArquivosTransferidos(arquivo.getSessaoId());
            progressoRedisService.limparProgresso(arquivo.getId());

            ArquivoCompletoEvent completoEvent = ArquivoCompletoEvent.builder()
                .arquivoId(arquivo.getId())
                .sessaoId(arquivo.getSessaoId())
                .nomeOriginal(arquivo.getNomeOriginal())
                .tamanhoBytes(arquivo.getTamanhoBytes())
                .tipoMime(analise.tipoMimeDetectado())
                .caminhoMinio(caminhoFinal)
                .remetenteId(arquivo.getRemetenteId())
                .build();

            rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TRANSFERENCIA,
                RabbitConfig.ROUTING_KEY_ARQUIVO,
                completoEvent
            );

            log.info("Arquivo aprovado na análise e finalizado: arquivo={}", arquivo.getId());

        } catch (Exception e) {
            log.error("Erro na análise de segurança do arquivo {}: {}", event.getArquivoId(), e.getMessage(), e);
            arquivo.setStatus(StatusArquivo.ERRO);
            arquivo.setMensagemErro("Erro na análise de segurança");
            arquivo.setAtualizadoEm(Instant.now());
            arquivoRepository.save(arquivo);
            arquivoRedisService.atualizarArquivo(arquivo);
            notificationService.notificarErroUpload(arquivo.getSessaoId(), arquivo.getId(), "Erro ao analisar arquivo");
        }
    }

    private void bloquearArquivo(Arquivo arquivo, String motivo) {
        String mensagem = motivo != null && !motivo.isBlank() ? motivo : "Arquivo malicioso detectado e bloqueado";

        arquivo.setStatus(StatusArquivo.BLOQUEADO);
        arquivo.setMensagemErro(mensagem);
        arquivo.setAtualizadoEm(Instant.now());

        arquivoRepository.save(arquivo);
        arquivoRedisService.atualizarArquivo(arquivo);

        progressoRedisService.limparProgresso(arquivo.getId());

        for (int i = 0; i < arquivo.getTotalChunks(); i++) {
            minioService.deleteChunk(arquivo.getSessaoId(), arquivo.getId(), i);
        }
        chunkArquivoRepository.deleteByArquivoId(arquivo.getId());

        notificationService.notificarArquivoBloqueado(arquivo.getSessaoId(), arquivo.getId(), mensagem);
        log.warn("Arquivo bloqueado: arquivo={} motivo={}", arquivo.getId(), mensagem);
    }
}
