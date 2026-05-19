package com.example.agentflow.service;

import java.util.Arrays;

import org.springframework.stereotype.Service;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.WorkflowState;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionService {

    private final SmartChatRouter smartChatRouter;
    private final AgentRegistry agentRegistry;
    private final ToolRegistry toolRegistry;

    public WorkflowState execute(String agentName, WorkflowState state) {
        AgentConfig config = agentRegistry.getAgentConfig(agentName);
        if (config == null) {
            log.warn("Agent [{}] not found in registry, skipping", agentName);
            state.put("_agent_" + agentName, "not_found");
            return state;
        }

        log.info("Executing agent [{}] with model preference [{}], tools: {}",
                agentName, config.getModelPreference(), config.getToolNames());

        ChatModel chatModel = resolveChatModel(config);
        Object[] toolObjects = resolveTools(config);

        String systemPrompt = buildSystemPrompt(config, state);
        String userMessage = buildUserMessage(state);

        AgentChatService aiService = AiServices.builder(AgentChatService.class)
                .chatModel(chatModel)
                .systemMessage(systemPrompt)
                .tools(toolObjects)
                .build();

        try {
            String response = aiService.chat(userMessage);
            state.put("_agent_" + agentName + "_response", response);
            log.info("Agent [{}] response: {}", agentName, response);
        } catch (Exception e) {
            log.error("Agent [{}] call failed", agentName, e);
            state.put("_agent_" + agentName + "_error", e.getMessage());
        }

        return state;
    }

    private ChatModel resolveChatModel(AgentConfig config) {
        ChatRequest request = ChatRequest.builder()
                .message("ping")
                .preferredModel(config.getModelPreference())
                .build();
        return smartChatRouter.route(request);
    }

    private Object[] resolveTools(AgentConfig config) {
        return toolRegistry.getTools(
                config.getToolNames() != null
                        ? config.getToolNames().toArray(new String[0])
                        : new String[0]);
    }

    private String buildSystemPrompt(AgentConfig config, WorkflowState state) {
        String prompt = config.getSystemPrompt();
        Object context = state.get("context");
        if (context != null) {
            prompt += "\n\n上下文信息：\n" + context;
        }
        return prompt;
    }

    private String buildUserMessage(WorkflowState state) {
        Object task = state.get("task");
        Object userInput = state.get("userInput");
        String message = "";
        if (task != null) message += task;
        if (userInput != null) message += "\n" + userInput;
        return message.isBlank() ? "请根据系统提示执行任务" : message;
    }

    interface AgentChatService {
        String chat(String userMessage);
    }
}
