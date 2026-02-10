package br.com.khawantech.files.transferencia.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import br.com.khawantech.files.transferencia.entity.Arquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArquivoRedisService {

    private static final String ARQUIVO_PREFIX = "arquivo:";
    private static final String ARQUIVO_HASH_PREFIX = "arquivo:hash:";
    private static final Duration TTL = Duration.ofHours(1);

    private final RedisTemplate<String, Arquivo> arquivoRedisTemplate;

    public void salvarArquivo(Arquivo arquivo) {
        String idKey = ARQUIVO_PREFIX + arquivo.getId();
        arquivoRedisTemplate.opsForValue().set(idKey, arquivo, TTL);

        if (arquivo.getHashConteudo() != null) {
            String hashKey = ARQUIVO_HASH_PREFIX + arquivo.getHashConteudo();
            arquivoRedisTemplate.opsForValue().set(hashKey, arquivo, TTL);
        }

        log.debug("Arquivo salvo no Redis: {}", arquivo.getId());
    }

    public Optional<Arquivo> buscarPorId(String arquivoId) {
        String key = ARQUIVO_PREFIX + arquivoId;
        Arquivo arquivo = arquivoRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(arquivo);
    }

    public Optional<Arquivo> buscarPorHash(String hashConteudo) {
        String key = ARQUIVO_HASH_PREFIX + hashConteudo;
        Arquivo arquivo = arquivoRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(arquivo);
    }

    public void atualizarArquivo(Arquivo arquivo) {
        salvarArquivo(arquivo);
    }

    public void removerArquivo(String arquivoId, String hashConteudo) {
        String idKey = ARQUIVO_PREFIX + arquivoId;
        arquivoRedisTemplate.delete(idKey);

        if (hashConteudo != null) {
            String hashKey = ARQUIVO_HASH_PREFIX + hashConteudo;
            arquivoRedisTemplate.delete(hashKey);
        }

        log.debug("Arquivo removido do Redis: {}", arquivoId);
    }

    public void removerArquivo(String arquivoId) {
        String idKey = ARQUIVO_PREFIX + arquivoId;
        arquivoRedisTemplate.delete(idKey);
        log.debug("Arquivo removido do Redis: {}", arquivoId);
    }
}
