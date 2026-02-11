package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;

@ChangeUnit(id = "V004_CreateChunksCollection", order = "004", author = "system")
public class V004CreateChunksCollection {

    private static final String COLLECTION_NAME = "chunks_arquivos";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        Document compoundIndexKeys = new Document()
            .append("arquivoId", 1)
            .append("numeroChunk", 1);

        List<IndexInfo> indexes = mongoTemplate.indexOps(COLLECTION_NAME).getIndexInfo();

        if (!hasIndexOnKeys(indexes, compoundIndexKeys)) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new CompoundIndexDefinition(compoundIndexKeys)
                    .unique()
                    .named("arquivo_chunk_unique_idx"));
        }

        if (!hasIndexOnKeys(indexes, new Document("arquivoId", 1))) {
            mongoTemplate.indexOps(COLLECTION_NAME)
                .createIndex(new Index()
                    .on("arquivoId", Sort.Direction.ASC)
                    .named("arquivo_idx"));
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        dropIndexIfExists(mongoTemplate, "arquivo_chunk_unique_idx");
        dropIndexIfExists(mongoTemplate, "arquivo_idx");
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
