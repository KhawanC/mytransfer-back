package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

@ChangeUnit(id = "V002_CreateSessoesCollection", order = "002", author = "system")
public class V002CreateSessoesCollection {

    private static final String COLLECTION_NAME = "sessoes";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        List<IndexInfo> indexes = mongoTemplate.indexOps(COLLECTION_NAME).getIndexInfo();

        if (!hasIndexOnKeys(indexes, new Document("hashConexao", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("hashConexao", Sort.Direction.ASC)
                    .unique()
                    .named("hash_conexao_unique_idx"));
        }

        if (!hasIndexOnKeys(indexes, new Document("usuarioCriadorId", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("usuarioCriadorId", Sort.Direction.ASC)
                    .named("usuario_criador_idx"));
        }

        if (!hasIndexOnKeys(indexes, new Document("usuarioConvidadoId", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("usuarioConvidadoId", Sort.Direction.ASC)
                    .sparse()
                    .named("usuario_convidado_idx"));
        }

        if (!hasIndexOnKeys(indexes, new Document("status", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("status", Sort.Direction.ASC)
                    .named("status_idx"));
        }

        if (!hasIndexOnKeys(indexes, new Document("expiraEm", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("expiraEm", Sort.Direction.ASC)
                    .expire(0, TimeUnit.SECONDS)
                    .named("expira_em_ttl_idx"));
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        dropIndexIfExists(mongoTemplate, "hash_conexao_unique_idx");
        dropIndexIfExists(mongoTemplate, "usuario_criador_idx");
        dropIndexIfExists(mongoTemplate, "usuario_convidado_idx");
        dropIndexIfExists(mongoTemplate, "status_idx");
        dropIndexIfExists(mongoTemplate, "expira_em_ttl_idx");
    }

    private boolean hasIndexOnKeys(List<IndexInfo> indexes, Document keys) {
        for (IndexInfo index : indexes) {
            if (indexFieldsMatch(index.getIndexFields(), keys)) {
                return true;
            }
        }
        return false;
    }

    private boolean indexFieldsMatch(List<IndexInfo.IndexField> fields, Document keys) {
        if (fields.size() != keys.size()) {
            return false;
        }

        int i = 0;
        for (var entry : keys.entrySet()) {
            IndexInfo.IndexField field = fields.get(i);
            if (!entry.getKey().equals(field.getKey())) {
                return false;
            }

            int direction = entry.getValue() instanceof Number
                ? ((Number) entry.getValue()).intValue()
                : 1;
            String fieldDirection = String.valueOf(field.getDirection());
            if (direction < 0) {
                if (field.getDirection() != null && !"DESC".equalsIgnoreCase(fieldDirection)) {
                    return false;
                }
            } else if (field.getDirection() != null && !"ASC".equalsIgnoreCase(fieldDirection)) {
                return false;
            }

            i++;
        }

        return true;
    }

    private void dropIndexIfExists(MongoTemplate mongoTemplate, String indexName) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(COLLECTION_NAME).getIndexInfo();
        for (IndexInfo index : indexes) {
            if (indexName.equals(index.getName())) {
                mongoTemplate.indexOps(COLLECTION_NAME).dropIndex(indexName);
                return;
            }
        }
    }
}
