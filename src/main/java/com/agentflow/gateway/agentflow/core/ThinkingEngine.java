package com.agentflow.gateway.agentflow.core;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.WorkflowState;

public interface ThinkingEngine {

    ThinkingMode getMode();

    AgentResponse execute(WorkflowState state, AgentConfig config);
}
