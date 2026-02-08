package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "V001_CreateUsersCollection", order = "001", author = "system")
public class V001CreateUsersCollection {

    private static final String COLLECTION_NAME = "users";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME);
        }

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("email", Sort.Direction.ASC)
                .unique()
                .named("email_unique_idx"));

        Document compoundIndexKeys = new Document()
            .append("email", 1)
            .append("authProvider", 1);
        
        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new CompoundIndexDefinition(compoundIndexKeys)
                .named("email_provider_idx"));

        mongoTemplate.indexOps(COLLECTION_NAME)
            .createIndex(new Index()
                .on("googleId", Sort.Direction.ASC)
                .sparse()
                .named("google_id_idx"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("email_unique_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("email_provider_idx");
        mongoTemplate.indexOps(COLLECTION_NAME).dropIndex("google_id_idx");
    }
}
