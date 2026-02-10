package br.com.khawantech.files.transferencia.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.transferencia.entity.ChatMensagem;

@Repository
public interface ChatMensagemRepository extends MongoRepository<ChatMensagem, String> {

    List<ChatMensagem> findBySessaoIdOrderByCriadoEmAsc(String sessaoId);

    long countBySessaoIdAndRemetenteIdNot(String sessaoId, String remetenteId);

    long countBySessaoIdAndRemetenteIdNotAndCriadoEmAfter(
        String sessaoId,
        String remetenteId,
        Instant criadoEm
    );

    void deleteBySessaoId(String sessaoId);
}
