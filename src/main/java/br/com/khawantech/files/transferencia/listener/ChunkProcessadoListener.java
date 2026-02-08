package br.com.khawantech.files.transferencia.listener;

import br.com.khawantech.files.transferencia.config.RabbitConfig;
import br.com.khawantech.files.transferencia.dto.ChunkRecebidoEvent;
import br.com.khawantech.files.transferencia.dto.ProgressoUploadResponse;
import br.com.khawantech.files.transferencia.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkProcessadoListener {

    private final WebSocketNotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.QUEUE_CHUNK_PROCESSADO)
    public void handleChunkProcessado(ChunkRecebidoEvent event) {
        log.debug("Chunk processado: arquivo={}, chunk={}/{}",
            event.getArquivoId(), event.getNumeroChunk() + 1, event.getTotalChunks());
        
        // Calcula progresso
        int chunksRecebidos = event.getNumeroChunk() + 1;
        double progressoPorcentagem = (chunksRecebidos * 100.0) / event.getTotalChunks();
        boolean completo = chunksRecebidos >= event.getTotalChunks();
        
        // Notifica progresso via WebSocket para todos os usuários da sessão
        notificationService.notificarProgresso(
            event.getSessaoId(),
            ProgressoUploadResponse.builder()
                .arquivoId(event.getArquivoId())
                .sessaoId(event.getSessaoId())
                .nomeArquivo(null)
                .chunkAtual(chunksRecebidos)
                .totalChunks(event.getTotalChunks())
                .progressoPorcentagem(progressoPorcentagem)
                .completo(completo)
                .urlDownload(null)
                .build()
        );
    }
}
