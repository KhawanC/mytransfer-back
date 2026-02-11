package br.com.khawantech.files.migration;

import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.util.List;

@ChangeUnit(id = "V007_CreateChatCollections", order = "007", author = "system")
public class V007CreateChatCollections {

    private static final String MENSAGENS_COLLECTION = "chat_mensagens";
    private static final String LEITURAS_COLLECTION = "chat_leituras";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(MENSAGENS_COLLECTION)) {
            mongoTemplate.createCollection(MENSAGENS_COLLECTION);
        }

        if (!mongoTemplate.collectionExists(LEITURAS_COLLECTION)) {
            mongoTemplate.createCollection(LEITURAS_COLLECTION);
        }

        Document mensagensCompound = new Document()
            .append("sessaoId", 1)
            .append("criadoEm", 1);

        List<IndexInfo> mensagensIndexes = mongoTemplate.indexOps(MENSAGENS_COLLECTION).getIndexInfo();

        if (!hasIndexOnKeys(mensagensIndexes, mensagensCompound)) {
            mongoTemplate.indexOps(MENSAGENS_COLLECTION)
                .createIndex(new CompoundIndexDefinition(mensagensCompound)
                    .named("sessao_criado_em_idx"));
        }

        if (!hasIndexOnKeys(mensagensIndexes, new Document("sessaoId", 1))) {
            mongoTemplate.indexOps(MENSAGENS_COLLECTION)
                .createIndex(new Index()
                    .on("sessaoId", Sort.Direction.ASC)
                    .named("sessao_idx"));
        }

        if (!hasIndexOnKeys(mensagensIndexes, new Document("remetenteId", 1))) {
            mongoTemplate.indexOps(MENSAGENS_COLLECTION)
                .createIndex(new Index()
                    .on("remetenteId", Sort.Direction.ASC)
                    .named("remetente_idx"));
        }

        if (!hasIndexOnKeys(mensagensIndexes, new Document("expiraEm", 1))) {
            mongoTemplate.indexOps(MENSAGENS_COLLECTION)
                .createIndex(new Index()
                    .on("expiraEm", Sort.Direction.ASC)
                    .expire(0, TimeUnit.SECONDS)
                    .named("chat_expira_em_ttl_idx"));
        }

        Document leiturasCompound = new Document()
            .append("sessaoId", 1)
            .append("usuarioId", 1);

        List<IndexInfo> leiturasIndexes = mongoTemplate.indexOps(LEITURAS_COLLECTION).getIndexInfo();

        if (!hasIndexOnKeys(leiturasIndexes, leiturasCompound)) {
            mongoTemplate.indexOps(LEITURAS_COLLECTION)
                .createIndex(new CompoundIndexDefinition(leiturasCompound)
                    .unique()
                    .named("sessao_usuario_unique_idx"));
        }

        if (!hasIndexOnKeys(leiturasIndexes, new Document("expiraEm", 1))) {
            mongoTemplate.indexOps(LEITURAS_COLLECTION)
                .createIndex(new Index()
                    .on("expiraEm", Sort.Direction.ASC)
                    .expire(0, TimeUnit.SECONDS)
                    .named("chat_leitura_expira_em_ttl_idx"));
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        dropIndexIfExists(mongoTemplate, MENSAGENS_COLLECTION, "sessao_criado_em_idx");
        dropIndexIfExists(mongoTemplate, MENSAGENS_COLLECTION, "sessao_idx");
        dropIndexIfExists(mongoTemplate, MENSAGENS_COLLECTION, "remetente_idx");
        dropIndexIfExists(mongoTemplate, MENSAGENS_COLLECTION, "chat_expira_em_ttl_idx");

        dropIndexIfExists(mongoTemplate, LEITURAS_COLLECTION, "sessao_usuario_unique_idx");
        dropIndexIfExists(mongoTemplate, LEITURAS_COLLECTION, "chat_leitura_expira_em_ttl_idx");
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

    private void dropIndexIfExists(MongoTemplate mongoTemplate, String collection, String indexName) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(collection).getIndexInfo();
        for (IndexInfo index : indexes) {
            if (indexName.equals(index.getName())) {
                mongoTemplate.indexOps(collection).dropIndex(indexName);
                return;
            }
        }
    }
}
