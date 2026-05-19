package com.example.agentflow.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.example.agentflow.config.ModelRegistryConfig.ModelProperties;
import com.example.agentflow.config.ModelRegistryConfig.ModelProperties.CostTier;
import com.example.agentflow.model.ChatRequest;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartChatRouter {

    private final Map<String, ChatModel> modelMap;
    private final ModelProperties modelProperties;
    private final HealthCheckService healthCheckService;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public ChatModel route(ChatRequest request) {
        String preferredModel = request.getPreferredModel();
        String message = request.getMessage();

        // 1. 基础路由：优先使用指定模型
        if (preferredModel != null && !preferredModel.isBlank()) {
            ChatModel model = modelMap.get(preferredModel);
            if (model == null) {
                throw new IllegalArgumentException("Unknown preferred model: " + preferredModel);
            }
            if (!healthCheckService.isHealthy(preferredModel)) {
                throw new IllegalStateException("Preferred model is unhealthy: " + preferredModel);
            }
            return model;
        }

        // 2. 成本路由：根据 token 估算选择 cost tier
        int estimatedTokens = estimateTokens(message);
        CostTier targetTier = resolveCostTier(estimatedTokens);
        log.debug("Estimated tokens: {}, target cost tier: {}", estimatedTokens, targetTier);

        // 3. 过滤符合条件的模型
        List<ModelProperties.ModelConfig> candidates = modelProperties.getList().stream()
                .filter(config -> config.getCostTier() == targetTier)
                .filter(config -> healthCheckService.isHealthy(config.getName()))
                .toList();

        if (candidates.isEmpty()) {
            // fallback: 尝试放宽 cost tier 限制
            candidates = modelProperties.getList().stream()
                    .filter(config -> healthCheckService.isHealthy(config.getName()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No healthy models available");
            }
        }

        // 4. 轮询选择一个模型
        int index = Math.abs(roundRobinIndex.getAndIncrement() % candidates.size());
        String selectedName = candidates.get(index).getName();
        log.info("Routed to model: {}", selectedName);
        return modelMap.get(selectedName);
    }

    private int estimateTokens(String message) {
        return message != null ? message.length() / 2 : 0;
    }

    private CostTier resolveCostTier(int estimatedTokens) {
        if (estimatedTokens < 500) {
            return CostTier.low;
        }
        if (estimatedTokens <= 2000) {
            return CostTier.medium;
        }
        return CostTier.high;
    }
}
