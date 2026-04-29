package com.agentflow.gateway.agentflow.core;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.WorkflowState;
import com.example.agentflow.service.SmartChatRouter;
import com.example.agentflow.service.ToolExecutionService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Component
public class ReActEngine extends AbstractThinkingEngine {

    private static final int MAX_ROUNDS = 10;

    private final SmartChatRouter smartChatRouter;
    private final ToolExecutionService toolExecutionService;

    public ReActEngine(SmartChatRouter smartChatRouter, ToolExecutionService toolExecutionService) {
        super(ThinkingMode.REACT);
        this.smartChatRouter = smartChatRouter;
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    protected AgentResponse doExecute(WorkflowState state, AgentConfig config) {
        AgentResponse response = new AgentResponse();
        List<String> conversation = new ArrayList<>();
        conversation.add("System: " + config.getSystemPrompt());
        conversation.add("User: " + String.valueOf(state.get("task")));

        ChatModel model = routeModel(config);
        ReActAgent agent = AiServices.builder(ReActAgent.class)
                .chatModel(model)
                .systemMessage(config.getSystemPrompt())
                .build();

        int round;
        for (round = 0; round < MAX_ROUNDS; round++) {
            String history = String.join("\n", conversation);
            String aiMessage = agent.react(history);

            ParsedReAct parsed = parseReAct(aiMessage);
            logStep(nextStep(), parsed.thought, parsed.action);

            response.getIntermediateSteps().add(
                    AgentResponse.StepResult.builder()
                            .step(round + 1)
                            .thought(parsed.thought)
                            .action(parsed.action)
                            .build());

            if (parsed.action == null || parsed.action.isBlank()) {
                response.setOutput(parsed.thought);
                break;
            }

            String toolResult = executeToolAction(parsed.toolName, parsed.actionInput);
            response.getToolCalls().add(
                    AgentResponse.ToolCall.builder()
                            .toolName(parsed.toolName)
                            .arguments(parsed.actionInput)
                            .result(toolResult)
                            .build());

            conversation.add("Assistant: " + aiMessage);
            conversation.add("Observation: " + toolResult);
        }

        if (round == MAX_ROUNDS) {
            response.setOutput(conversation.get(conversation.size() - 1));
        }
        return response;
    }

    private ParsedReAct parseReAct(String aiOutput) {
        ParsedReAct parsed = new ParsedReAct();
        if (aiOutput == null) return parsed;

        String[] lines = aiOutput.split("\n");
        StringBuilder thought = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Thought:") || trimmed.startsWith("思考：")) {
                thought.append(trimmed.substring(trimmed.indexOf(":") + 1).trim());
            } else if (trimmed.startsWith("Action:") || trimmed.startsWith("行动：")) {
                parsed.action = trimmed.substring(trimmed.indexOf(":") + 1).trim();
                String action = parsed.action;
                if (action.contains("[")) {
                    parsed.toolName = action.substring(0, action.indexOf("[")).trim();
                    parsed.actionInput = action.substring(
                            action.indexOf("[") + 1, action.lastIndexOf("]")).trim();
                }
            } else {
                thought.append(" ").append(trimmed);
            }
        }
        parsed.thought = thought.toString().trim();
        return parsed;
    }

    private String executeToolAction(String toolName, String actionInput) {
        if (toolName == null) return actionInput;
        try {
            return toolExecutionService.execute(toolName,
                    java.util.Map.of("query", actionInput));
        } catch (Exception e) {
            log.warn("Tool [{}] execution failed: {}", toolName, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private ChatModel routeModel(AgentConfig config) {
        return smartChatRouter.route(ChatRequest.builder()
                .preferredModel(config.getModelPreference())
                .message("")
                .build());
    }

    private static class ParsedReAct {
        String thought;
        String action;
        String toolName;
        String actionInput;
    }

    interface ReActAgent {
        String react(String history);
    }
}
