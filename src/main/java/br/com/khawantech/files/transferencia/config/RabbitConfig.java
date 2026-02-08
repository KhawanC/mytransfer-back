package br.com.khawantech.files.transferencia.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_TRANSFERENCIA = "transferencia.exchange";
    public static final String EXCHANGE_DLQ = "transferencia.dlq.exchange";
    
    public static final String QUEUE_CHUNK_PROCESSADO = "transferencia.chunk.processado";
    public static final String QUEUE_ARQUIVO_COMPLETO = "transferencia.arquivo.completo";
    public static final String QUEUE_SESSAO_ATUALIZADA = "transferencia.sessao.atualizada";
    
    public static final String QUEUE_CHUNK_DLQ = "transferencia.chunk.dlq";
    public static final String QUEUE_ARQUIVO_DLQ = "transferencia.arquivo.dlq";
    public static final String QUEUE_SESSAO_DLQ = "transferencia.sessao.dlq";

    public static final String ROUTING_KEY_CHUNK = "chunk.processado";
    public static final String ROUTING_KEY_ARQUIVO = "arquivo.completo";
    public static final String ROUTING_KEY_SESSAO = "sessao.atualizada";
    
    public static final String ROUTING_KEY_CHUNK_DLQ = "chunk.dlq";
    public static final String ROUTING_KEY_ARQUIVO_DLQ = "arquivo.dlq";
    public static final String ROUTING_KEY_SESSAO_DLQ = "sessao.dlq";

    @Bean
    public DirectExchange transferenciaExchange() {
        return new DirectExchange(EXCHANGE_TRANSFERENCIA, true, false);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(EXCHANGE_DLQ, true, false);
    }

    @Bean
    public Queue chunkProcessadoQueue() {
        return QueueBuilder.durable(QUEUE_CHUNK_PROCESSADO)
            .withArgument("x-dead-letter-exchange", EXCHANGE_DLQ)
            .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CHUNK_DLQ)
            .build();
    }

    @Bean
    public Queue arquivoCompletoQueue() {
        return QueueBuilder.durable(QUEUE_ARQUIVO_COMPLETO)
            .withArgument("x-dead-letter-exchange", EXCHANGE_DLQ)
            .withArgument("x-dead-letter-routing-key", ROUTING_KEY_ARQUIVO_DLQ)
            .build();
    }

    @Bean
    public Queue sessaoAtualizadaQueue() {
        return QueueBuilder.durable(QUEUE_SESSAO_ATUALIZADA)
            .withArgument("x-dead-letter-exchange", EXCHANGE_DLQ)
            .withArgument("x-dead-letter-routing-key", ROUTING_KEY_SESSAO_DLQ)
            .build();
    }

    @Bean
    public Queue chunkDlqQueue() {
        return QueueBuilder.durable(QUEUE_CHUNK_DLQ).build();
    }

    @Bean
    public Queue arquivoDlqQueue() {
        return QueueBuilder.durable(QUEUE_ARQUIVO_DLQ).build();
    }

    @Bean
    public Queue sessaoDlqQueue() {
        return QueueBuilder.durable(QUEUE_SESSAO_DLQ).build();
    }

    @Bean
    public Binding chunkProcessadoBinding(Queue chunkProcessadoQueue, DirectExchange transferenciaExchange) {
        return BindingBuilder.bind(chunkProcessadoQueue).to(transferenciaExchange).with(ROUTING_KEY_CHUNK);
    }

    @Bean
    public Binding arquivoCompletoBinding(Queue arquivoCompletoQueue, DirectExchange transferenciaExchange) {
        return BindingBuilder.bind(arquivoCompletoQueue).to(transferenciaExchange).with(ROUTING_KEY_ARQUIVO);
    }

    @Bean
    public Binding sessaoAtualizadaBinding(Queue sessaoAtualizadaQueue, DirectExchange transferenciaExchange) {
        return BindingBuilder.bind(sessaoAtualizadaQueue).to(transferenciaExchange).with(ROUTING_KEY_SESSAO);
    }

    @Bean
    public Binding chunkDlqBinding(Queue chunkDlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(chunkDlqQueue).to(dlqExchange).with(ROUTING_KEY_CHUNK_DLQ);
    }

    @Bean
    public Binding arquivoDlqBinding(Queue arquivoDlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(arquivoDlqQueue).to(dlqExchange).with(ROUTING_KEY_ARQUIVO_DLQ);
    }

    @Bean
    public Binding sessaoDlqBinding(Queue sessaoDlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(sessaoDlqQueue).to(dlqExchange).with(ROUTING_KEY_SESSAO_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(1000, 2.0, 10000)
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter, RetryTemplate retryTemplate) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setRetryTemplate(retryTemplate);
        return template;
    }
}
