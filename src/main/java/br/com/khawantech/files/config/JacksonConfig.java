package br.com.khawantech.files.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    /**
     * Configuração global do ObjectMapper com limites ajustados para suportar
     * upload de arquivos grandes em Base64.
     * 
     * Limite de string aumentado para 50 MB para acomodar chunks de arquivos.
     */
    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        
        // Adicionar módulo de data/hora Java 8
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Configurar limites de leitura de stream
        // 50 MB para strings (suficiente para chunks de arquivos em Base64)
        StreamReadConstraints streamReadConstraints = StreamReadConstraints.builder()
            .maxStringLength(50_000_000) // 50 MB
            .build();
        
        objectMapper.getFactory().setStreamReadConstraints(streamReadConstraints);
        
        return objectMapper;
    }
}
