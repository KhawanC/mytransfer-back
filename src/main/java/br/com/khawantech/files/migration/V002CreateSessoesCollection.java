package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

@ChangeUnit(id = "V002_CreateSessoesCollection", order = "002", author = "system")
public class V002CreateSessoesCollection {

    private static final String COLLECTION_NAME = "sessoes";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("hashConexao", Sort.Direction.ASC)
                .unique()
                .named("hash_conexao_unique_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("usuarioCriadorId", Sort.Direction.ASC)
                .named("usuario_criador_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("usuarioConvidadoId", Sort.Direction.ASC)
                .sparse()
                .named("usuario_convidado_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .named("status_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("expiraEm", Sort.Direction.ASC)
                .expire(0, TimeUnit.SECONDS)
                .named("expira_em_ttl_idx"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("hash_conexao_unique_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("usuario_criador_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("usuario_convidado_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("status_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("expira_em_ttl_idx");
    }
}
