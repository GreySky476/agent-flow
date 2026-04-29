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
import dev.langchain4j.service.UserMessage;

@Component
public class ReflectionEngine extends AbstractThinkingEngine {

    private static final int MAX_REFLECTIONS = 2;

    private final SmartChatRouter smartChatRouter;
    private final ToolExecutionService toolExecutionService;

    public ReflectionEngine(SmartChatRouter smartChatRouter, ToolExecutionService toolExecutionService) {
        super(ThinkingMode.REFLECTION);
        this.smartChatRouter = smartChatRouter;
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    protected AgentResponse doExecute(WorkflowState state, AgentConfig config) {
        AgentResponse response = new AgentResponse();
        List<String> reflections = new ArrayList<>();

        ChatModel model = routeModel(config);

        // 1. 初始执行
        GenerateAgent initialAgent = AiServices.builder(GenerateAgent.class)
                .chatModel(model)
                .systemMessage(config.getSystemPrompt())
                .build();

        String currentOutput = initialAgent.generate(String.valueOf(state.get("task")));
        int step = nextStep();
        response.getIntermediateSteps().add(
                AgentResponse.StepResult.builder()
                        .step(step).action("initial").observation(currentOutput).build());
        reflections.add("初始输出: " + currentOutput);

        // 2. 反思-修正循环
        for (int r = 0; r < MAX_REFLECTIONS; r++) {
            step = nextStep();

            // 反思
            ReflectAgent reflector = AiServices.builder(ReflectAgent.class)
                    .chatModel(routeModel(config))
                    .systemMessage("你是严谨的质量评估助手。请评估以下输出，指出问题并提供改进建议。" +
                            "从以下维度评估：正确性、相关性、完整性。")
                    .build();

            String feedback = reflector.reflect(
                    String.valueOf(state.get("task")), currentOutput);
            logStep(step, "Reflection round " + (r + 1), feedback);
            reflections.add("反思" + (r + 1) + ": " + feedback);

            // 修正
            RefineAgent refiner = AiServices.builder(RefineAgent.class)
                    .chatModel(routeModel(config))
                    .systemMessage(config.getSystemPrompt() + "\n" +
                            "请根据以下反馈改进你的回答。")
                    .build();

            String refined = refiner.refine(currentOutput, feedback);
            response.getIntermediateSteps().add(
                    AgentResponse.StepResult.builder()
                            .step(step)
                            .thought("反思 #" + (r + 1))
                            .observation(refined)
                            .build());

            if (refined == null || refined.equals(currentOutput)) {
                log.info("Refinement converged at round {}", r + 1);
                break;
            }
            currentOutput = refined;
        }

        response.setOutput(currentOutput);
        response.getIntermediateSteps().add(
                AgentResponse.StepResult.builder()
                        .step(nextStep())
                        .thought("反思历史")
                        .observation(String.join("\n---\n", reflections))
                        .build());
        return response;
    }

    private ChatModel routeModel(AgentConfig config) {
        return smartChatRouter.route(ChatRequest.builder()
                .preferredModel(config.getModelPreference()).message("").build());
    }

    interface GenerateAgent {
        String generate(String task);
    }

    interface ReflectAgent {
        @UserMessage("原始任务: {{task}}\n\n当前输出:\n{{currentOutput}}\n\n请从正确性、相关性、完整性三个维度评估这份输出，指出问题并提供改进建议。")
        String reflect(String task, String currentOutput);
    }

    interface RefineAgent {
        @UserMessage("原始输出:\n{{currentOutput}}\n\n改进建议:\n{{feedback}}\n\n请根据改进建议输出修正后的版本。")
        String refine(String currentOutput, String feedback);
    }
}
