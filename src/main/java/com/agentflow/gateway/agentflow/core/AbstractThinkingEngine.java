package com.agentflow.gateway.agentflow.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.WorkflowState;

import lombok.Getter;

public abstract class AbstractThinkingEngine implements ThinkingEngine {

    @Getter
    private final ThinkingMode mode;

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicInteger stepCounter = new AtomicInteger(0);

    protected AbstractThinkingEngine(ThinkingMode mode) {
        this.mode = mode;
    }

    @Override
    public final ThinkingMode getMode() {
        return mode;
    }

    @Override
    public AgentResponse execute(WorkflowState state, AgentConfig config) {
        stepCounter.set(0);
        log.info("[{}][{}] Starting execution, mode={}", config.getName(), getClass().getSimpleName(), mode);
        long start = System.currentTimeMillis();

        try {
            AgentResponse response = doExecute(state, config);
            response.setCompleted(true);
            log.info("[{}][{}] Completed in {}ms, {} steps, output length={}",
                    config.getName(), getClass().getSimpleName(),
                    System.currentTimeMillis() - start,
                    stepCounter.get(),
                    response.getOutput() != null ? response.getOutput().length() : 0);
            return response;
        } catch (Exception e) {
            log.error("[{}][{}] Execution failed at step {}",
                    config.getName(), getClass().getSimpleName(), stepCounter.get(), e);
            AgentResponse response = new AgentResponse();
            response.setCompleted(false);
            response.setError(e.getMessage());
            return response;
        }
    }

    protected abstract AgentResponse doExecute(WorkflowState state, AgentConfig config);

    protected int nextStep() {
        return stepCounter.incrementAndGet();
    }

    protected void logStep(int step, String thought, String action) {
        log.debug("[Step {}] Thought: {}\n  Action: {}", step, thought, action);
    }
}
