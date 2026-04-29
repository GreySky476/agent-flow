package com.agentflow.gateway.agentflow.core;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
public class AgentResponse {

    private String output;
    private List<ToolCall> toolCalls = new ArrayList<>();
    private List<StepResult> intermediateSteps = new ArrayList<>();
    private boolean completed;
    private String error;

    @Data
    @Builder
    public static class ToolCall {
        private String toolName;
        private String arguments;
        private String result;
    }

    @Data
    @Builder
    public static class StepResult {
        private int step;
        private String thought;
        private String action;
        private String observation;
    }
}
