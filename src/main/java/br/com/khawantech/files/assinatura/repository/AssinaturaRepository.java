package br.com.khawantech.files.assinatura.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.assinatura.entity.Assinatura;
import br.com.khawantech.files.assinatura.entity.StatusAssinatura;

@Repository
public interface AssinaturaRepository extends MongoRepository<Assinatura, String> {

    Optional<Assinatura> findFirstByUsuarioIdOrderByCriadaEmDesc(String usuarioId);

    List<Assinatura> findByUsuarioId(String usuarioId);

    Optional<Assinatura> findByAssinaturaExternaId(String assinaturaExternaId);

    Optional<Assinatura> findByCobrancaExternaId(String cobrancaExternaId);

    Optional<Assinatura> findByReferenciaExterna(String referenciaExterna);

    List<Assinatura> findByStatusInAndPeriodoFimBefore(List<StatusAssinatura> statuses, Instant agora);
}
