package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOptions;

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
        
        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new CompoundIndexDefinition(compoundIndexKeys)
                .unique()
                .named("arquivo_chunk_unique_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("arquivoId", Sort.Direction.ASC)
                .named("arquivo_idx"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("arquivo_chunk_unique_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("arquivo_idx");
    }
}
