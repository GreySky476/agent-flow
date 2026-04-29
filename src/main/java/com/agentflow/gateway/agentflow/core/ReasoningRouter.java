package com.agentflow.gateway.agentflow.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.WorkflowState;
import com.example.agentflow.service.SmartChatRouter;

import dev.langchain4j.model.chat.ChatModel;

@Service
public class ReasoningRouter {

    private static final Logger log = LoggerFactory.getLogger(ReasoningRouter.class);

    private static final String CLASSIFY_PROMPT =
            "你是一个任务分类器。请将以下用户请求归入以下类别之一：" +
            "REACT（通用查询或简单动作）、" +
            "PLAN_EXECUTE（复杂多步骤任务）、" +
            "REWOO（需要收集大量独立信息）、" +
            "REFLECTION（需要自我修正或质量优化）。" +
            "只返回类别名，不要解释。\n请求：";

    private final Map<ThinkingMode, ThinkingEngine> engineMap;
    private final SmartChatRouter smartChatRouter;

    public ReasoningRouter(List<ThinkingEngine> engines, SmartChatRouter smartChatRouter) {
        this.engineMap = engines.stream()
                .collect(Collectors.toMap(ThinkingEngine::getMode, e -> e));
        this.smartChatRouter = smartChatRouter;
    }

    public AgentResponse route(WorkflowState state) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String userQuery = String.valueOf(state.get("userQuery") != null
                ? state.get("userQuery") : state.get("task"));

        ThinkingMode mode;
        String reason;

        if (state.get("modeOverride") != null) {
            mode = ThinkingMode.valueOf(String.valueOf(state.get("modeOverride")).toUpperCase());
            reason = "override";
        } else {
            mode = classify(userQuery);
            reason = "classified";
        }

        log.info("[{}] Routing to {} mode (reason={}, queryHint={})",
                requestId, mode, reason,
                userQuery != null ? userQuery.substring(0, Math.min(50, userQuery.length())) : "");

        AgentConfig config = buildConfig(state);
        return executeWithFallback(mode, state, config, requestId);
    }

    public AgentResponse executeWithFallback(ThinkingMode mode, WorkflowState state,
                                              AgentConfig config) {
        return executeWithFallback(mode, state, config,
                UUID.randomUUID().toString().substring(0, 8));
    }

    private AgentResponse executeWithFallback(ThinkingMode mode, WorkflowState state,
                                               AgentConfig config, String requestId) {
        ThinkingEngine engine = engineMap.get(mode);
        if (engine == null) {
            log.warn("[{}] No engine for mode {}, falling back to REACT", requestId, mode);
            engine = engineMap.get(ThinkingMode.REACT);
        }

        try {
            return engine.execute(state, config);
        } catch (Exception e) {
            log.error("[{}] Engine {} failed: {} — falling back to REACT",
                    requestId, mode, e.getMessage());
            ThinkingEngine fallback = engineMap.get(ThinkingMode.REACT);
            if (fallback == null) {
                throw new RuntimeException("All engines failed, including REACT fallback", e);
            }
            try {
                return fallback.execute(state, config);
            } catch (Exception e2) {
                throw new RuntimeException("Both " + mode + " and REACT fallback failed", e2);
            }
        }
    }

    public ThinkingMode classify(String userQuery) {
        try {
            ChatModel model = smartChatRouter.route(ChatRequest.builder()
                    .message(userQuery)
                    .build());
            String response = model.chat(CLASSIFY_PROMPT + userQuery);
            String cleaned = response.trim().toUpperCase();
            for (ThinkingMode m : ThinkingMode.values()) {
                if (cleaned.contains(m.name())) return m;
            }
            return ThinkingMode.REACT;
        } catch (Exception e) {
            log.warn("Task classification failed, defaulting to REACT: {}", e.getMessage());
            return ThinkingMode.REACT;
        }
    }

    private AgentConfig buildConfig(WorkflowState state) {
        AgentConfig config = new AgentConfig();
        config.setName(String.valueOf(state.get("_definitionName") != null
                ? state.get("_definitionName") : "dynamic"));
        config.setSystemPrompt(String.valueOf(state.get("systemPrompt") != null
                ? state.get("systemPrompt") : "你是一个智能助手"));
        config.setModelPreference(String.valueOf(state.get("modelPreference") != null
                ? state.get("modelPreference") : ""));
        return config;
    }
}
