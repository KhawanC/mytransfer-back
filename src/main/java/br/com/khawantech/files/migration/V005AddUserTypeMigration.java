package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeUnit(id = "V005_AddUserType", order = "005", author = "system")
public class V005AddUserTypeMigration {

    private static final String COLLECTION_NAME = "users";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        Query query = new Query();
        
        Update update = new Update()
            .set("userType", "FREE")
            .setOnInsert("guestCreatedAt", null);
        
        com.mongodb.client.result.UpdateResult result = mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        
        System.out.println("Migration V005: Updated " + result.getModifiedCount() + " users with userType=FREE");
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        Query query = new Query();
        
        Update update = new Update()
            .unset("userType")
            .unset("guestCreatedAt");
        
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
    }
}
