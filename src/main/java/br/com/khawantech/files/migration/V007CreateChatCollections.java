package br.com.khawantech.files.migration;

import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;

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

        Document mensagensCompound = new Document()
            .append("sessaoId", 1)
            .append("criadoEm", 1);

        mongoTemplate.indexOps(MENSAGENS_COLLECTION)
            .createIndex(new CompoundIndexDefinition(mensagensCompound)
                .named("sessao_criado_em_idx"));

        mongoTemplate.indexOps(MENSAGENS_COLLECTION)
            .createIndex(new Index()
                .on("sessaoId", Sort.Direction.ASC)
                .named("sessao_idx"));

        mongoTemplate.indexOps(MENSAGENS_COLLECTION)
            .createIndex(new Index()
                .on("remetenteId", Sort.Direction.ASC)
                .named("remetente_idx"));

        mongoTemplate.indexOps(MENSAGENS_COLLECTION)
            .createIndex(new Index()
                .on("expiraEm", Sort.Direction.ASC)
                .expire(0, TimeUnit.SECONDS)
                .named("chat_expira_em_ttl_idx"));

        Document leiturasCompound = new Document()
            .append("sessaoId", 1)
            .append("usuarioId", 1);

        mongoTemplate.indexOps(LEITURAS_COLLECTION)
            .createIndex(new CompoundIndexDefinition(leiturasCompound)
                .unique()
                .named("sessao_usuario_unique_idx"));

        mongoTemplate.indexOps(LEITURAS_COLLECTION)
            .createIndex(new Index()
                .on("expiraEm", Sort.Direction.ASC)
                .expire(0, TimeUnit.SECONDS)
                .named("chat_leitura_expira_em_ttl_idx"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(MENSAGENS_COLLECTION).dropIndex("sessao_criado_em_idx");
        mongoTemplate.indexOps(MENSAGENS_COLLECTION).dropIndex("sessao_idx");
        mongoTemplate.indexOps(MENSAGENS_COLLECTION).dropIndex("remetente_idx");
        mongoTemplate.indexOps(MENSAGENS_COLLECTION).dropIndex("chat_expira_em_ttl_idx");

        mongoTemplate.indexOps(LEITURAS_COLLECTION).dropIndex("sessao_usuario_unique_idx");
        mongoTemplate.indexOps(LEITURAS_COLLECTION).dropIndex("chat_leitura_expira_em_ttl_idx");
    }
}
