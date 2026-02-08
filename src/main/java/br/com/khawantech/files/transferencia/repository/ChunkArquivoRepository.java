package br.com.khawantech.files.transferencia.repository;

import br.com.khawantech.files.transferencia.entity.ChunkArquivo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChunkArquivoRepository extends MongoRepository<ChunkArquivo, String> {

    List<ChunkArquivo> findByArquivoIdOrderByNumeroChunkAsc(String arquivoId);

    Optional<ChunkArquivo> findByArquivoIdAndNumeroChunk(String arquivoId, int numeroChunk);

    int countByArquivoId(String arquivoId);

    boolean existsByArquivoIdAndNumeroChunk(String arquivoId, int numeroChunk);

    void deleteByArquivoId(String arquivoId);

    List<ChunkArquivo> findByArquivoIdIn(List<String> arquivoIds);
}
