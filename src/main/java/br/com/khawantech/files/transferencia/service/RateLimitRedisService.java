package br.com.khawantech.files.transferencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitRedisService {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final int MAX_CHUNKS_PER_MINUTE = 200;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> transferenciaRedisTemplate;

    public boolean verificarLimiteRequisicoes(String usuarioId) {
        String key = RATE_LIMIT_PREFIX + "req:" + usuarioId;
        return verificarLimite(key, MAX_REQUESTS_PER_MINUTE);
    }

    public boolean verificarLimiteChunks(String usuarioId) {
        String key = RATE_LIMIT_PREFIX + "chunk:" + usuarioId;
        return verificarLimite(key, MAX_CHUNKS_PER_MINUTE);
    }

    public boolean verificarLimiteSessao(String sessaoId) {
        String key = RATE_LIMIT_PREFIX + "sessao:" + sessaoId;
        return verificarLimite(key, MAX_REQUESTS_PER_MINUTE * 2);
    }

    private boolean verificarLimite(String key, int maxRequests) {
        long timestamp = Instant.now().toEpochMilli();
        long windowStart = timestamp - WINDOW.toMillis();

        transferenciaRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        Long count = transferenciaRedisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= maxRequests) {
            log.warn("Rate limit excedido para: {}", key);
            return false;
        }

        transferenciaRedisTemplate.opsForZSet().add(key, String.valueOf(timestamp), timestamp);
        transferenciaRedisTemplate.expire(key, WINDOW.plusMinutes(1).toSeconds(), TimeUnit.SECONDS);

        return true;
    }

    public long getRequestsRestantes(String usuarioId) {
        String key = RATE_LIMIT_PREFIX + "req:" + usuarioId;
        Long count = transferenciaRedisTemplate.opsForZSet().zCard(key);
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - (count != null ? count : 0));
    }

    public void resetarLimite(String usuarioId) {
        String reqKey = RATE_LIMIT_PREFIX + "req:" + usuarioId;
        String chunkKey = RATE_LIMIT_PREFIX + "chunk:" + usuarioId;

        transferenciaRedisTemplate.delete(reqKey);
        transferenciaRedisTemplate.delete(chunkKey);
    }
}
