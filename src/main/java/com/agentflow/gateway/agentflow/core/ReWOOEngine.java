package com.agentflow.gateway.agentflow.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.model.WorkflowState;
import com.example.agentflow.service.SmartChatRouter;
import com.example.agentflow.service.ToolExecutionService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;

@Component
public class ReWOOEngine extends AbstractThinkingEngine {

    private static final int MAX_TOOLS = 5;
    private static final int MAX_RESULT_LENGTH = 1000;

    private final SmartChatRouter smartChatRouter;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReWOOEngine(SmartChatRouter smartChatRouter, ToolExecutionService toolExecutionService) {
        super(ThinkingMode.REWOO);
        this.smartChatRouter = smartChatRouter;
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    protected AgentResponse doExecute(WorkflowState state, AgentConfig config) {
        AgentResponse response = new AgentResponse();
        ChatModel model = routeModel(config);

        // 1. 一次性规划所有工具调用
        PlannerAgent planner = AiServices.builder(PlannerAgent.class)
                .chatModel(model)
                .systemMessage(config.getSystemPrompt() + "\n" +
                        "请输出 JSON 工具调用数组 [{tool: 工具名, input: 参数}]，数量不超过 " + MAX_TOOLS + " 个")
                .build();

        String planJson = planner.plan(String.valueOf(state.get("task")));
        List<ToolPlan> toolPlans = parseToolPlans(planJson);

        if (toolPlans.size() > MAX_TOOLS) {
            toolPlans = toolPlans.subList(0, MAX_TOOLS);
        }

        log.info("ReWOO planned {} tool calls", toolPlans.size());

        // 2. 并发执行所有工具
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (ToolPlan tp : toolPlans) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                logStep(nextStep(), "Calling " + tp.tool, tp.input);
                try {
                    return toolExecutionService.execute(tp.tool, Map.of("query", tp.input));
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            }));
        }

        // 3. 收集结果
        for (int i = 0; i < toolPlans.size(); i++) {
            try {
                String result = futures.get(i).get(30, TimeUnit.SECONDS);
                result = truncate(result, MAX_RESULT_LENGTH);
                response.getToolCalls().add(
                        AgentResponse.ToolCall.builder()
                                .toolName(toolPlans.get(i).tool)
                                .arguments(toolPlans.get(i).input)
                                .result(result)
                                .build());
            } catch (Exception e) {
                response.getToolCalls().add(
                        AgentResponse.ToolCall.builder()
                                .toolName(toolPlans.get(i).tool)
                                .result("Error: " + e.getMessage())
                                .build());
            }
        }

        // 4. 最终总结
        SummarizerAgent summarizer = AiServices.builder(SummarizerAgent.class)
                .chatModel(routeModel(config))
                .systemMessage("根据工具调用结果进行最终回答")
                .build();

        String toolResults = response.getToolCalls().stream()
                .map(tc -> "[" + tc.getToolName() + "]: " + tc.getResult())
                .reduce("", (a, b) -> a + "\n" + b);

        response.setOutput(summarizer.summarize(
                String.valueOf(state.get("task")), toolResults));
        return response;
    }

    private List<ToolPlan> parseToolPlans(String json) {
        try {
            String cleaned = stripCodeFences(json);
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, ToolPlan.class);
            return objectMapper.readValue(cleaned, type);
        } catch (Exception e) {
            log.warn("Failed to parse tool plans: {}", e.getMessage());
            return List.of();
        }
    }

    private String stripCodeFences(String raw) {
        String s = raw;
        if (s.contains("```json")) s = s.substring(s.indexOf("```json") + 7);
        else if (s.contains("```")) s = s.substring(s.indexOf("```") + 3);
        if (s.contains("```")) s = s.substring(0, s.indexOf("```"));
        return s.trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...(省略 " + (text.length() - maxLen) + " 字符)";
    }

    private ChatModel routeModel(AgentConfig config) {
        return smartChatRouter.route(ChatRequest.builder()
                .preferredModel(config.getModelPreference()).message("").build());
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class ToolPlan {
        public String tool;
        public String input;
    }

    interface PlannerAgent {
        String plan(String task);
    }

    interface SummarizerAgent {
        @UserMessage("任务: {{task}}\n\n工具调用结果:\n{{toolResults}}\n\n请综合以上信息回答用户问题。")
        String summarize(String task, String toolResults);
    }
}
