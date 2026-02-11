package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "V002_CreateSessoesCollection", order = "002", author = "system")
public class V002CreateSessoesCollection {

    private static final String COLLECTION_NAME = "sessoes";

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
