package br.com.khawantech.files.transferencia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.transferencia.entity.Arquivo;
import br.com.khawantech.files.transferencia.entity.StatusArquivo;

@Repository
public interface ArquivoRepository extends MongoRepository<Arquivo, String> {

    List<Arquivo> findBySessaoId(String sessaoId);

    List<Arquivo> findBySessaoIdAndStatus(String sessaoId, StatusArquivo status);

    Optional<Arquivo> findByHashConteudo(String hashConteudo);

    Optional<Arquivo> findBySessaoIdAndHashConteudo(String sessaoId, String hashConteudo);

    int countBySessaoId(String sessaoId);

    List<Arquivo> findByStatus(StatusArquivo status);

    void deleteBySessaoId(String sessaoId);

    List<Arquivo> findBySessaoIdAndRemetenteIdAndStatusIn(
        String sessaoId, 
        String remetenteId, 
        List<StatusArquivo> statuses
    );
}
