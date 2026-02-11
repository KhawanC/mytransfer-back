package br.com.khawantech.files.migration;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "V009_AddPlanoRecomendado", order = "009", author = "system")
public class V009AddPlanoRecomendado {

    private static final String COLLECTION = "planos_assinatura";
    private static final String PLANO_MENSAL_ID = "da6d3529-1b49-41b2-bf2f-943340af7e7f";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            return;
        }

        mongoTemplate.updateMulti(new Query(), new Update().set("recomendado", false), COLLECTION);
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(PLANO_MENSAL_ID)),
            new Update().set("recomendado", true),
            COLLECTION
        );
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            return;
        }

        mongoTemplate.updateMulti(new Query(), new Update().unset("recomendado"), COLLECTION);
    }
}
