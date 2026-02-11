package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "V003_CreateArquivosCollection", order = "003", author = "system")
public class V003CreateArquivosCollection {

    private static final String COLLECTION_NAME = "arquivos";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
    }
}
