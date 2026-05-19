package com.example.agentflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Data;

@Configuration
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagConfig {

    @Bean
    @ConfigurationProperties(prefix = "rag.pgvector")
    public PgvectorProperties pgvectorProperties() {
        return new PgvectorProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "rag.embedding")
    public EmbeddingProperties embeddingProperties() {
        return new EmbeddingProperties();
    }

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(PgvectorProperties pg, EmbeddingProperties emb) {
        return PgVectorEmbeddingStore.builder()
                .host(pg.getHost())
                .port(pg.getPort())
                .database(pg.getDatabase())
                .user(pg.getUser())
                .password(pg.getPassword())
                .table("rag_embeddings")
                .dimension(emb.getDimension())
                .build();
    }

    @Data
    public static class PgvectorProperties {
        private String host = "localhost";
        private int port = 5432;
        private String database = "agentflow";
        private String user = "postgres";
        private String password = "yourpassword";
    }

    @Data
    public static class EmbeddingProperties {
        private String apiKey = "sk-your-key";
        private String baseUrl = "https://api.deepseek.com/v1";
        private String modelName = "text-embedding-3-small";
        private int dimension = 1024;
    }
}
