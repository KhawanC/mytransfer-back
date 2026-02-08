package br.com.khawantech.files.transferencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressoUploadRedisService {

    private static final String PROGRESSO_PREFIX = "upload:progresso:";
    private static final String CHUNKS_RECEBIDOS_PREFIX = "upload:chunks:";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Object> transferenciaRedisTemplate;

    public void registrarChunkRecebido(String arquivoId, int numeroChunk, int totalChunks) {
        String chunksKey = CHUNKS_RECEBIDOS_PREFIX + arquivoId;
        String progressoKey = PROGRESSO_PREFIX + arquivoId;

        transferenciaRedisTemplate.opsForSet().add(chunksKey, String.valueOf(numeroChunk));
        transferenciaRedisTemplate.expire(chunksKey, TTL);

        Long chunksRecebidos = transferenciaRedisTemplate.opsForSet().size(chunksKey);
        if (chunksRecebidos != null) {
            double progresso = (double) chunksRecebidos / totalChunks * 100.0;
            transferenciaRedisTemplate.opsForValue().set(progressoKey, progresso, TTL);
        }

        log.debug("Chunk {} registrado para arquivo {}", numeroChunk, arquivoId);
    }

    public int getChunksRecebidos(String arquivoId) {
        String key = CHUNKS_RECEBIDOS_PREFIX + arquivoId;
        Long size = transferenciaRedisTemplate.opsForSet().size(key);
        return size != null ? size.intValue() : 0;
    }

    public Set<Object> getChunksRecebidosSet(String arquivoId) {
        String key = CHUNKS_RECEBIDOS_PREFIX + arquivoId;
        return transferenciaRedisTemplate.opsForSet().members(key);
    }

    public boolean chunkJaRecebido(String arquivoId, int numeroChunk) {
        String key = CHUNKS_RECEBIDOS_PREFIX + arquivoId;
        return Boolean.TRUE.equals(
            transferenciaRedisTemplate.opsForSet().isMember(key, String.valueOf(numeroChunk))
        );
    }

    public double getProgressoPorcentagem(String arquivoId) {
        String key = PROGRESSO_PREFIX + arquivoId;
        Object progresso = transferenciaRedisTemplate.opsForValue().get(key);
        return progresso != null ? ((Number) progresso).doubleValue() : 0.0;
    }

    public boolean uploadCompleto(String arquivoId, int totalChunks) {
        return getChunksRecebidos(arquivoId) >= totalChunks;
    }

    public void limparProgresso(String arquivoId) {
        String chunksKey = CHUNKS_RECEBIDOS_PREFIX + arquivoId;
        String progressoKey = PROGRESSO_PREFIX + arquivoId;

        transferenciaRedisTemplate.delete(chunksKey);
        transferenciaRedisTemplate.delete(progressoKey);

        log.debug("Progresso limpo para arquivo {}", arquivoId);
    }
}
