package com.example.agentflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.gateway.agentflow.core.ReasoningRouter;
import com.agentflow.gateway.agentflow.core.ThinkingEngine;
import com.agentflow.gateway.agentflow.core.ThinkingMode;
import com.example.agentflow.model.WorkflowState;
import com.example.agentflow.service.ToolRegistry;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HealthCheckController {

    private final ToolRegistry toolRegistry;
    private final List<ThinkingEngine> engines;
    private final ReasoningRouter reasoningRouter;

    @GetMapping("/api/v1/health/mcp")
    public List<Map<String, Object>> mcpStatus() {
        return toolRegistry.getToolMap().values().stream()
                .filter(t -> t.getType() == ToolRegistry.ToolType.MCP)
                .map(t -> Map.<String, Object>of(
                        "name", t.getName(),
                        "server", t.getSource(),
                        "description", t.getDescription(),
                        "enabled", t.isEnabled(),
                        "exportable", t.isExportable()))
                .toList();
    }

    @GetMapping("/api/v1/health/engines")
    public List<Map<String, Object>> engineStatus() {
        return engines.stream()
                .map(e -> Map.<String, Object>of(
                        "mode", e.getMode().name(),
                        "class", e.getClass().getSimpleName(),
                        "status", "REGISTERED"))
                .toList();
    }

    @PostMapping("/api/v1/test/route")
    public Map<String, Object> testRoute(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return Map.of("error", "Missing 'query' in request body");
        }

        WorkflowState state = new WorkflowState();
        state.put("task", query);
        state.put("userQuery", query);

        ThinkingMode mode = reasoningRouter.classify(query);
        return Map.of(
                "query", query,
                "classifiedMode", mode.name(),
                "enginesAvailable", engines.stream()
                        .map(e -> e.getMode().name())
                        .toList());
    }
}
