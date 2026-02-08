package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "V003_CreateArquivosCollection", order = "003", author = "system")
public class V003CreateArquivosCollection {

    private static final String COLLECTION_NAME = "arquivos";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("sessaoId", Sort.Direction.ASC)
                .named("sessao_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("hashConteudo", Sort.Direction.ASC)
                .named("hash_conteudo_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .named("status_idx"));

        Document compoundIndexKeys = new Document()
            .append("sessaoId", 1)
            .append("status", 1);
        
        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new CompoundIndexDefinition(compoundIndexKeys)
                .named("sessao_status_idx"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("sessao_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("hash_conteudo_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("status_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("sessao_status_idx");
    }
}
