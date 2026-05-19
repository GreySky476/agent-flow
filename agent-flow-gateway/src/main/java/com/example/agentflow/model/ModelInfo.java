package com.example.agentflow.model;

import com.example.agentflow.config.ModelRegistryConfig.ModelProperties.CostTier;

import dev.langchain4j.model.chat.ChatModel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelInfo {
    private String name;
    private CostTier costTier;
    private ChatModel model;
}
