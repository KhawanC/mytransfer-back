package br.com.khawantech.files.transferencia.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.transferencia.entity.ChatLeitura;

@Repository
public interface ChatLeituraRepository extends MongoRepository<ChatLeitura, String> {

    Optional<ChatLeitura> findBySessaoIdAndUsuarioId(String sessaoId, String usuarioId);

    void deleteBySessaoId(String sessaoId);
}
