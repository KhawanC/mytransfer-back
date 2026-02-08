package br.com.khawantech.files.transferencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockRedisService {

    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> transferenciaRedisTemplate;

    public String adquirirLock(String recurso) {
        return adquirirLock(recurso, DEFAULT_LOCK_TIMEOUT);
    }

    public String adquirirLock(String recurso, Duration timeout) {
        String key = LOCK_PREFIX + recurso;
        String lockId = UUID.randomUUID().toString();

        Boolean acquired = transferenciaRedisTemplate.opsForValue()
            .setIfAbsent(key, lockId, timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock adquirido para recurso: {} com id: {}", recurso, lockId);
            return lockId;
        }

        log.debug("Falha ao adquirir lock para recurso: {}", recurso);
        return null;
    }

    public boolean liberarLock(String recurso, String lockId) {
        String key = LOCK_PREFIX + recurso;
        Object currentLockId = transferenciaRedisTemplate.opsForValue().get(key);

        if (lockId.equals(currentLockId)) {
            transferenciaRedisTemplate.delete(key);
            log.debug("Lock liberado para recurso: {}", recurso);
            return true;
        }

        log.warn("Tentativa de liberar lock com ID diferente para recurso: {}", recurso);
        return false;
    }

    public boolean lockExiste(String recurso) {
        String key = LOCK_PREFIX + recurso;
        return Boolean.TRUE.equals(transferenciaRedisTemplate.hasKey(key));
    }

    public String getLockChunk(String arquivoId, int numeroChunk) {
        return "chunk:" + arquivoId + ":" + numeroChunk;
    }

    public String getLockArquivo(String arquivoId) {
        return "arquivo:" + arquivoId;
    }

    public String getLockSessao(String sessaoId) {
        return "sessao:" + sessaoId;
    }
}
