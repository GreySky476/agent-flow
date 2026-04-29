package com.example.agentflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.gateway.memory.MemoryStore.Message;
import com.agentflow.gateway.memory.SmartMemoryService;
import com.example.agentflow.model.RetrievalResult;
import com.example.agentflow.service.GraphExtractionService;
import com.example.agentflow.service.GraphRagRetriever;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/debug")
@RequiredArgsConstructor
public class DebugPhase2Controller {

    private final GraphExtractionService graphExtractionService;
    private final GraphRagRetriever graphRagRetriever;
    private final SmartMemoryService smartMemoryService;

    @PostMapping("/graph/extract")
    public Map<String, Object> extractGraph(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return Map.of("error", "Missing 'text' in request body");
        }
        return graphExtractionService.extractEntitiesAndRelations(text);
    }

    @PostMapping("/graph/query")
    public Map<String, Object> queryGraph(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return Map.of("error", "Missing 'query' in request body");
        }
        List<RetrievalResult> results = graphRagRetriever.retrieve(query, 5);
        return Map.of(
                "query", query,
                "results", results,
                "count", results.size());
    }

    @GetMapping("/memory/{sessionId}")
    public Map<String, Object> memoryDetail(@PathVariable String sessionId) {
        List<Message> recent = smartMemoryService.getRecent(sessionId, Integer.MAX_VALUE);
        String summary = smartMemoryService.getSummary(sessionId);
        Map<String, String> entities = smartMemoryService.getEntityMemory(sessionId);

        int estimatedTokens = recent.stream().mapToInt(m -> m.content().length() / 2).sum();

        return Map.of(
                "sessionId", sessionId,
                "shortTerm", recent.stream()
                        .map(m -> Map.of("role", m.role(), "content", m.content()))
                        .toList(),
                "summary", summary != null ? summary : "",
                "entities", entities,
                "messageCount", recent.size(),
                "estimatedTokens", estimatedTokens,
                "hasSummary", summary != null && !summary.isBlank(),
                "entityCount", entities.size()
        );
    }

    @PostMapping("/memory/{sessionId}/compress")
    public Map<String, Object> compressMemory(@PathVariable String sessionId) {
        smartMemoryService.compress(sessionId);
        return Map.of("sessionId", sessionId, "success", true);
    }
}
