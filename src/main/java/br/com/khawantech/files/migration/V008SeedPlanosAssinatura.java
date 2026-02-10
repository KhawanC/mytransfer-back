package br.com.khawantech.files.migration;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "V008_SeedPlanosAssinatura", order = "008", author = "system")
public class V008SeedPlanosAssinatura {

    private static final String COLLECTION = "planos_assinatura";

    private static final Document PLANO_SEMANAL = new Document()
        .append("_id", "8d0c0f8b-1c88-4f9d-94a2-1b20162af0f4")
        .append("nome", "Assinatura Semanal")
        .append("precoCentavos", 1500)
        .append("duracaoDias", 7);

    private static final Document PLANO_MENSAL = new Document()
        .append("_id", "da6d3529-1b49-41b2-bf2f-943340af7e7f")
        .append("nome", "Assinatura Mensal")
        .append("precoCentavos", 4500)
        .append("duracaoDias", 30);

    private static final Document PLANO_TRIMESTRAL = new Document()
        .append("_id", "2ff2a4bb-0126-49b7-8b4e-8e24d8125b2f")
        .append("nome", "Assinatura Trimestral")
        .append("precoCentavos", 17500)
        .append("duracaoDias", 90);

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        List<Document> planos = List.of(PLANO_SEMANAL, PLANO_MENSAL, PLANO_TRIMESTRAL);
        for (Document plano : planos) {
            String id = plano.getString("_id");
            if (id == null) {
                continue;
            }
            Document existing = mongoTemplate.findById(id, Document.class, COLLECTION);
            if (existing == null) {
                mongoTemplate.insert(plano, COLLECTION);
            }
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.remove(PLANO_SEMANAL, COLLECTION);
        mongoTemplate.remove(PLANO_MENSAL, COLLECTION);
        mongoTemplate.remove(PLANO_TRIMESTRAL, COLLECTION);
    }
}
