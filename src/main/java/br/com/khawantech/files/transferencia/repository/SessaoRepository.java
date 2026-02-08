package br.com.khawantech.files.transferencia.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.transferencia.entity.Sessao;
import br.com.khawantech.files.transferencia.entity.StatusSessao;

@Repository
public interface SessaoRepository extends MongoRepository<Sessao, String> {

    Optional<Sessao> findByHashConexao(String hashConexao);

    List<Sessao> findByUsuarioCriadorIdAndStatus(String usuarioCriadorId, StatusSessao status);

    List<Sessao> findByUsuarioConvidadoIdAndStatus(String usuarioConvidadoId, StatusSessao status);

    List<Sessao> findByStatusAndExpiraEmBefore(StatusSessao status, Instant dataExpiracao);

    List<Sessao> findByStatusIn(List<StatusSessao> statuses);

    List<Sessao> findByExpiraEmBeforeAndStatusNot(Instant dataExpiracao, StatusSessao status);

    boolean existsByHashConexao(String hashConexao);

    List<Sessao> findByUsuarioCriadorIdOrUsuarioConvidadoIdOrderByCriadaEmDesc(String usuarioCriadorId, String usuarioConvidadoId);
    
    @Query("{ $or: [ { 'usuarioCriadorId': ?0 }, { 'usuarioConvidadoId': ?0 }, { 'usuarioConvidadoPendenteId': ?0 } ] }")
    List<Sessao> findSessoesDoUsuario(String usuarioId);
}
