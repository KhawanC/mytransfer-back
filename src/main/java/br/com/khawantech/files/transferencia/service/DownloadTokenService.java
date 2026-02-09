package br.com.khawantech.files.transferencia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String TOKEN_PREFIX = "download_token:";
    private static final long TOKEN_EXPIRATION_MINUTES = 5;

    public String gerarToken(String arquivoId, String usuarioId) {
        String token = UUID.randomUUID().toString();
        String key = TOKEN_PREFIX + token;
        String value = arquivoId + ":" + usuarioId;

        redisTemplate.opsForValue().set(key, value, TOKEN_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        log.debug("Token de download gerado: {} para arquivo: {}", token, arquivoId);

        return token;
    }

    public String[] validarEConsumirToken(String token) {
        String key = TOKEN_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.warn("Token de download inválido ou expirado: {}", token);
            return null;
        }

        redisTemplate.delete(key);

        String[] parts = value.split(":");
        if (parts.length != 2) {
            log.error("Token com formato inválido: {}", token);
            return null;
        }

        log.debug("Token de download validado e consumido: {}", token);
        return parts;
    }
}
