package com.example.agentflow.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.gateway.memory.SmartMemoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final SmartMemoryService smartMemoryService;

    @GetMapping("/{sessionId}/summary")
    public Map<String, Object> getSummary(@PathVariable String sessionId) {
        String summary = smartMemoryService.getSummary(sessionId);
        return Map.of("sessionId", sessionId, "summary", summary != null ? summary : "");
    }

    @GetMapping("/{sessionId}/entities")
    public Map<String, Object> getEntities(@PathVariable String sessionId) {
        Map<String, String> entities = smartMemoryService.getEntityMemory(sessionId);
        return Map.of("sessionId", sessionId, "entities", entities);
    }

    @GetMapping("/{sessionId}/status")
    public Map<String, Object> getStatus(@PathVariable String sessionId) {
        var recent = smartMemoryService.getRecent(sessionId, Integer.MAX_VALUE);
        int msgCount = recent.size();
        int estimatedTokens = recent.stream().mapToInt(m -> m.content().length() / 2).sum();
        String summary = smartMemoryService.getSummary(sessionId);

        return Map.of(
                "sessionId", sessionId,
                "shortTermMessages", msgCount,
                "estimatedTokens", estimatedTokens,
                "hasSummary", summary != null && !summary.isBlank(),
                "entities", smartMemoryService.getEntityMemory(sessionId).size()
        );
    }

    @PostMapping("/{sessionId}/compress")
    public Map<String, Object> compress(@PathVariable String sessionId) {
        smartMemoryService.compress(sessionId);
        return Map.of("sessionId", sessionId, "success", true);
    }
}
