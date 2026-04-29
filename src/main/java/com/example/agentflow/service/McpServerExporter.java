package com.example.agentflow.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.agentflow.service.ToolRegistry.ToolEntry;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("${agentflow.mcp.export.endpoint:/mcp/agentflow}")
@ConditionalOnProperty(name = "agentflow.mcp.export.enabled", havingValue = "true")
@RequiredArgsConstructor
public class McpServerExporter {

    private static final Logger log = LoggerFactory.getLogger(McpServerExporter.class);

    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;

    @Value("${agentflow.mcp.export.api-key:}")
    private String apiKey;

    @GetMapping("/tools")
    public List<Map<String, String>> listTools(@RequestHeader(value = "X-API-Key", required = false) String headerKey) {
        checkAuth(headerKey);
        return toolRegistry.getToolMap().values().stream()
                .filter(ToolEntry::isExportable)
                .map(t -> Map.of(
                        "name", t.getName(),
                        "description", t.getDescription()))
                .collect(Collectors.toList());
    }

    @PostMapping("/execute")
    public Map<String, Object> executeTool(@RequestBody ToolCallRequest request,
                                            @RequestHeader(value = "X-API-Key", required = false) String headerKey) {
        checkAuth(headerKey);
        ToolEntry entry = toolRegistry.getTool(request.toolName());
        if (entry == null || !entry.isExportable()) {
            return Map.of("error", "Tool not found or not exportable: " + request.toolName());
        }

        log.info("MCP export call: tool=[{}] params={}", request.toolName(), request.arguments());
        String result = toolExecutionService.execute(request.toolName(), request.arguments());
        log.info("MCP export result: tool=[{}] length={}", request.toolName(),
                result != null ? result.length() : 0);

        return Map.of("result", result);
    }

    private void checkAuth(String headerKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            if (headerKey == null || !headerKey.equals(apiKey)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
            }
        }
    }

    public record ToolCallRequest(String toolName, Map<String, Object> arguments) {}
}
