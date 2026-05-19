package com.example.agentflow.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;

@Configuration
@EnableConfigurationProperties
@lombok.RequiredArgsConstructor
public class ModelRegistryConfig {

    private final ConfigurableListableBeanFactory beanFactory;

    @Bean
    @ConfigurationProperties(prefix = "models")
    public ModelProperties modelProperties() {
        return new ModelProperties();
    }

    @Bean
    public Map<String, ChatModel> modelMap(ModelProperties properties) {
        if (properties.getList() == null) {
            return Map.of();
        }
        Map<String, ChatModel> map = new HashMap<>();
        for (ModelProperties.ModelConfig config : properties.getList()) {
            ChatModel model = createChatModel(config);
            map.put(config.getName(), model);
            beanFactory.registerSingleton(config.getName(), model);
        }
        return map;
    }

    private ChatModel createChatModel(ModelProperties.ModelConfig config) {
        return switch (config.getType()) {
            case openai, qwen -> OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .baseUrl(config.getBaseUrl())
                    .build();
            case azure -> AzureOpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .endpoint(config.getBaseUrl())
                    .deploymentName(config.getModelName())
                    .build();
            case ollama -> OllamaChatModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModelName())
                    .build();
        };
    }

    @Data
    public static class ModelProperties {
        private List<ModelConfig> list;

        @Data
        public static class ModelConfig {
            private String name;
            private ModelType type;
            private String apiKey;
            private String baseUrl;
            private String modelName;
            private CostTier costTier = CostTier.medium;
        }

        public enum ModelType {
            openai,
            azure,
            ollama,
            qwen
        }

        public enum CostTier {
            low,
            medium,
            high
        }
    }
}
