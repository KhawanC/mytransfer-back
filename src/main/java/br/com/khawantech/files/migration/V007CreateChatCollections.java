package br.com.khawantech.files.migration;

import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;


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
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
    }
}
