package br.com.khawantech.files.transferencia.service;

import br.com.khawantech.files.transferencia.config.TransferenciaProperties;
import br.com.khawantech.files.transferencia.entity.Sessao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoRedisService {

    private static final String SESSAO_PREFIX = "sessao:";
    private static final String SESSAO_HASH_PREFIX = "sessao:hash:";

    private final RedisTemplate<String, Sessao> sessaoRedisTemplate;
    private final TransferenciaProperties properties;

    public void salvarSessao(Sessao sessao) {
        String idKey = SESSAO_PREFIX + sessao.getId();
        String hashKey = SESSAO_HASH_PREFIX + sessao.getHashConexao();
        Duration ttl = Duration.ofMinutes((long) (properties.getCacheTtlHoras() * 60));

        sessaoRedisTemplate.opsForValue().set(idKey, sessao, ttl);
        sessaoRedisTemplate.opsForValue().set(hashKey, sessao, ttl);
        
        log.debug("Sessão salva no Redis: {}", sessao.getId());
    }

    public Optional<Sessao> buscarPorId(String sessaoId) {
        String key = SESSAO_PREFIX + sessaoId;
        Sessao sessao = sessaoRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(sessao);
    }

    public Optional<Sessao> buscarPorHash(String hashConexao) {
        String key = SESSAO_HASH_PREFIX + hashConexao;
        Sessao sessao = sessaoRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(sessao);
    }

    public void atualizarSessao(Sessao sessao) {
        salvarSessao(sessao);
    }

    public void invalidarSessao(String sessaoId, String hashConexao) {
        String idKey = SESSAO_PREFIX + sessaoId;
        String hashKey = SESSAO_HASH_PREFIX + hashConexao;

        sessaoRedisTemplate.delete(idKey);
        sessaoRedisTemplate.delete(hashKey);
        
        log.debug("Sessão invalidada no Redis: {}", sessaoId);
    }

    public boolean existePorId(String sessaoId) {
        String key = SESSAO_PREFIX + sessaoId;
        return Boolean.TRUE.equals(sessaoRedisTemplate.hasKey(key));
    }

    public boolean existePorHash(String hashConexao) {
        String key = SESSAO_HASH_PREFIX + hashConexao;
        return Boolean.TRUE.equals(sessaoRedisTemplate.hasKey(key));
    }
}
