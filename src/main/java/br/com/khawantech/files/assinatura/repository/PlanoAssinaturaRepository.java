package br.com.khawantech.files.assinatura.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.assinatura.entity.PlanoAssinatura;

@Repository
public interface PlanoAssinaturaRepository extends MongoRepository<PlanoAssinatura, String> {
}
