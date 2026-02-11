package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "V001_CreateUsersCollection", order = "001", author = "system")
public class V001CreateUsersCollection {

    private static final String COLLECTION_NAME = "users";

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
