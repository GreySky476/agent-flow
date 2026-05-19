package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmNodeExecutor {

    private final SmartChatRouter smartChatRouter;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public WorkflowState execute(WorkflowNode node, WorkflowState state) {
        Map<String, Object> config = parseConfig(node.getConfigJson());

        String modelName = (String) config.get("modelName");
        String systemPrompt = (String) config.getOrDefault("systemPrompt", "你是一个智能助手");

        ChatModel chatModel = resolveChatModel(modelName);

        List<Object> toolObjects = new ArrayList<>();

        Map<String, Object> ragConfig = (Map<String, Object>) config.get("rag");
        if (ragConfig != null) {
            List<Integer> kbIds = (List<Integer>) ragConfig.get("knowledgeBaseIds");
            Integer topK = ragConfig.get("topK") != null ? (Integer) ragConfig.get("topK") : 5;
            if (kbIds != null && !kbIds.isEmpty()) {
                RagToolProvider ragTool = new RagToolProvider(state, kbIds, topK);
                toolObjects.add(ragTool);
            }
        }

        List<String> toolNames = (List<String>) config.get("toolNames");
        if (toolNames != null && !toolNames.isEmpty()) {
            Object[] tools = toolRegistry.getTools(toolNames.toArray(new String[0]));
            for (Object tool : tools) {
                toolObjects.add(tool);
            }
        }

        String userMessage = buildUserMessage(state);

        LlmChatService aiService = AiServices.builder(LlmChatService.class)
                .chatModel(chatModel)
                .systemMessage(systemPrompt)
                .tools(toolObjects.toArray())
                .build();

        try {
            String response = aiService.chat(userMessage);
            state.put("_llm_" + node.getNodeId() + "_response", response);
            log.info("LLM node [{}] response length: {}", node.getNodeId(),
                    response != null ? response.length() : 0);
        } catch (Exception e) {
            log.error("LLM node [{}] call failed", node.getNodeId(), e);
            state.put("_llm_" + node.getNodeId() + "_error", e.getMessage());
        }

        return state;
    }

    private ChatModel resolveChatModel(String modelName) {
        ChatRequest request = ChatRequest.builder()
                .message("ping")
                .preferredModel(modelName)
                .build();
        return smartChatRouter.route(request);
    }

    private String buildUserMessage(WorkflowState state) {
        Object userInput = state.get("userInput");
        Object task = state.get("task");
        StringBuilder sb = new StringBuilder();
        if (task != null) sb.append(task);
        if (userInput != null) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(userInput);
        }
        if (sb.isEmpty()) return "请根据系统提示执行任务";
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM config: {}", e.getMessage());
            return Map.of();
        }
    }

    interface LlmChatService {
        String chat(String userMessage);
    }
}
