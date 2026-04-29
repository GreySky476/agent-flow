package com.agentflow.gateway.agentflow.core;

import java.time.Duration;
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
public class PlanExecuteEngine extends AbstractThinkingEngine {

    private static final int MAX_STEPS = 10;
    private static final int STEP_TIMEOUT_SEC = 30;
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(120);

    private final SmartChatRouter smartChatRouter;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanExecuteEngine(SmartChatRouter smartChatRouter, ToolExecutionService toolExecutionService) {
        super(ThinkingMode.PLAN_EXECUTE);
        this.smartChatRouter = smartChatRouter;
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    protected AgentResponse doExecute(WorkflowState state, AgentConfig config) {
        AgentResponse response = new AgentResponse();
        long deadline = System.currentTimeMillis() + TOTAL_TIMEOUT.toMillis();

        ChatModel model = routeModel(config);
        PlanAgent planAgent = AiServices.builder(PlanAgent.class)
                .chatModel(model)
                .systemMessage(config.getSystemPrompt() + "\n" +
                        "请生成 JSON 计划数组，每个元素包含 {step: 步骤描述, tool: 工具名(可选), toolInput: 输入(可选)}")
                .build();

        String planJson = planAgent.plan(String.valueOf(state.get("task")));
        List<PlanStep> steps = parsePlan(planJson);

        if (steps.isEmpty()) {
            throw new RuntimeException("Plan generation failed, suggest falling back to ReAct mode. Raw output: " + planJson);
        }

        log.info("Generated plan with {} steps", steps.size());

        for (int i = 0; i < steps.size() && i < MAX_STEPS; i++) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("Plan execution timed out after " + TOTAL_TIMEOUT);
            }

            PlanStep step = steps.get(i);
            int stepNum = nextStep();
            logStep(stepNum, step.step, step.tool);

            if (step.tool != null && !step.tool.isBlank()) {
                try {
                    String result = CompletableFuture
                            .supplyAsync(() -> toolExecutionService.execute(step.tool,
                                    Map.of("query", step.toolInput != null ? step.toolInput : step.step)))
                            .get(STEP_TIMEOUT_SEC, TimeUnit.SECONDS);
                    response.getToolCalls().add(
                            AgentResponse.ToolCall.builder()
                                    .toolName(step.tool).arguments(step.toolInput).result(result)
                                    .build());
                    response.getIntermediateSteps().add(
                            AgentResponse.StepResult.builder()
                                    .step(stepNum).action(step.tool).observation(result).build());
                } catch (Exception e) {
                    log.warn("Step [{}] tool [{}] failed: {}", stepNum, step.tool, e.getMessage());
                    response.getIntermediateSteps().add(
                            AgentResponse.StepResult.builder()
                                    .step(stepNum).action(step.tool).observation("Error: " + e.getMessage()).build());
                }
            } else {
                response.getIntermediateSteps().add(
                        AgentResponse.StepResult.builder()
                                .step(stepNum).thought(step.step).build());
            }
        }

        // 最终总结
        ExecutorAgent execAgent = AiServices.builder(ExecutorAgent.class)
                .chatModel(routeModel(config))
                .systemMessage("根据执行结果进行最终总结")
                .build();

        response.setOutput(execAgent.summarize(String.valueOf(state.get("task")),
                String.valueOf(response.getIntermediateSteps())));
        return response;
    }

    private List<PlanStep> parsePlan(String json) {
        try {
            String cleaned = json;
            if (cleaned.contains("```json")) {
                cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
                if (cleaned.contains("```")) {
                    cleaned = cleaned.substring(0, cleaned.indexOf("```"));
                }
            } else if (cleaned.contains("```")) {
                cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
                if (cleaned.contains("```")) {
                    cleaned = cleaned.substring(0, cleaned.indexOf("```"));
                }
            }
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, PlanStep.class);
            return objectMapper.readValue(cleaned.trim(), type);
        } catch (Exception e) {
            log.warn("Failed to parse plan JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private ChatModel routeModel(AgentConfig config) {
        return smartChatRouter.route(ChatRequest.builder()
                .preferredModel(config.getModelPreference()).message("").build());
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class PlanStep {
        public String step;
        public String tool;
        public String toolInput;
    }

    interface PlanAgent {
        String plan(String task);
    }

    interface ExecutorAgent {
        @UserMessage("任务: {{task}}\n\n执行日志:\n{{executionLog}}\n\n请根据执行日志进行最终总结。")
        String summarize(String task, String executionLog);
    }
}
