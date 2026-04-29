package com.agentflow.gateway.memory;

import java.util.List;
import java.util.Map;

public interface MemoryStore {

    void add(String sessionId, String role, String content);

    List<Message> getRecent(String sessionId, int n);

    void updateSummary(String sessionId, String summary);

    String getSummary(String sessionId);

    void updateEntityMemory(String sessionId, Map<String, String> entities);

    Map<String, String> getEntityMemory(String sessionId);

    record Message(String role, String content, long timestamp) {}
}
