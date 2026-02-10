package br.com.khawantech.files.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeUnit(id = "V006_AddConversionFields", order = "006", author = "system")
public class V006AddConversionFieldsMigration {

    private static final String COLLECTION_NAME = "arquivos";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        Query query = new Query();
        
        Update update = new Update()
            .set("conversivel", false)
            .setOnInsert("arquivoOriginalId", null)
            .setOnInsert("formatoConvertido", null);
        
        com.mongodb.client.result.UpdateResult result = mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        
        System.out.println("Migration V006: Updated " + result.getModifiedCount() + " arquivos with conversion fields");
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        Query query = new Query();
        
        Update update = new Update()
            .unset("conversivel")
            .unset("arquivoOriginalId")
            .unset("formatoConvertido");
        
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
    }
}
