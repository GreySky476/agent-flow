package com.example.agentflow.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Getter
public class WorkflowState {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String workflowInstanceId;
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public WorkflowState() {
        this.workflowInstanceId = UUID.randomUUID().toString().substring(0, 8);
    }

    public WorkflowState(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public WorkflowState merge(Map<String, Object> initialInput) {
        if (initialInput != null) {
            data.putAll(initialInput);
        }
        return this;
    }

    public Object get(String key) {
        return data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) data.get(key);
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object remove(String key) {
        return data.remove(key);
    }

    /** @deprecated use {@link #getData()} instead */
    @Deprecated
    public Map<String, Object> getVariables() {
        return data;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "workflowInstanceId", workflowInstanceId,
                    "data", data));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize WorkflowState", e);
        }
    }

    public static WorkflowState fromJson(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            String instanceId = (String) map.get("workflowInstanceId");
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) map.get("data");
            WorkflowState state = new WorkflowState(
                    instanceId != null ? instanceId : UUID.randomUUID().toString().substring(0, 8));
            if (dataMap != null) {
                state.data.putAll(dataMap);
            }
            return state;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize WorkflowState", e);
        }
    }
}
