package com.example.agentflow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.ChatRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExtractionService {

    private static final int MAX_CHUNK_TOKENS = 2000;
    private static final int MAX_RETRIES = 1;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SmartChatRouter smartChatRouter;

    @Value("${graph.entity-relation-prompt-template}")
    private String promptTemplate;

    public Map<String, Object> extractEntitiesAndRelations(String text) {
        Instant start = Instant.now();
        List<Map<String, Object>> allEntities = new ArrayList<>();
        List<Map<String, Object>> allRelations = new ArrayList<>();
        int totalTokens = 0;

        List<String> chunks = splitText(text, MAX_CHUNK_TOKENS);
        log.info("Graph extraction: {} chunks from {} chars", chunks.size(), text.length());

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            int chunkTokens = estimateTokens(chunk);
            totalTokens += chunkTokens;

            Map<String, Object> result = extractFromChunk(chunk, i + 1, chunks.size());
            if (result == null) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) result.getOrDefault("entities", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relations = (List<Map<String, Object>>) result.getOrDefault("relations", List.of());

            allEntities.addAll(entities);
            allRelations.addAll(relations);
            log.info("Chunk {}/{} extracted: {} entities, {} relations",
                    i + 1, chunks.size(), entities.size(), relations.size());
        }

        // 去重实体
        List<Map<String, Object>> dedupedEntities = deduplicateEntities(allEntities);

        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        log.info("Graph extraction complete: {} entities, {} relations, {}ms, ~{} tokens",
                dedupedEntities.size(), allRelations.size(), elapsedMs, totalTokens);

        return Map.of(
                "entities", dedupedEntities,
                "relations", allRelations,
                "stats", Map.of(
                        "totalEntities", dedupedEntities.size(),
                        "totalRelations", allRelations.size(),
                        "elapsedMs", elapsedMs,
                        "estimatedTokens", totalTokens));
    }

    private Map<String, Object> extractFromChunk(String chunk, int chunkIndex, int totalChunks) {
        ChatModel model = smartChatRouter.route(ChatRequest.builder().message(chunk).build());
        String prompt = promptTemplate + "\n\n文本 (第" + chunkIndex + "/" + totalChunks + "段):\n" + chunk;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String raw = model.chat(prompt);
                String json = extractJson(raw);
                Map<String, Object> result = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                if (result.containsKey("entities") && result.containsKey("relations")) {
                    return result;
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    prompt += "\n\n上次输出格式错误，请严格输出JSON: {\"entities\":[...], \"relations\":[...]}";
                } else {
                    log.warn("Chunk {} extraction failed after {} retries: {}", chunkIndex, MAX_RETRIES + 1, e.getMessage());
                }
            }
        }
        return Map.of("entities", List.of(), "relations", List.of());
    }

    private List<Map<String, Object>> deduplicateEntities(List<Map<String, Object>> entities) {
        Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        Set<String> idMap = new LinkedHashSet<>();

        for (Map<String, Object> entity : entities) {
            Object id = entity.get("id");
            if (id == null) continue;
            String idStr = String.valueOf(id).toLowerCase().trim();

            if (!idMap.add(idStr)) {
                // 合并 properties
                Map<String, Object> existing = seen.get(idStr);
                @SuppressWarnings("unchecked")
                Map<String, Object> existingProps = (Map<String, Object>) existing.getOrDefault("properties", Map.of());
                @SuppressWarnings("unchecked")
                Map<String, Object> newProps = (Map<String, Object>) entity.getOrDefault("properties", Map.of());
                Map<String, Object> merged = new LinkedHashMap<>(existingProps);
                merged.putAll(newProps);
                existing.put("properties", merged);
            } else {
                seen.put(idStr, entity);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private List<String> splitText(String text, int maxTokens) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\n\n|(?<=\\. )");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (estimateTokens(current + para) > maxTokens && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(para).append(" ");
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private String extractJson(String raw) {
        String s = raw;
        int start = s.indexOf("{");
        int end = s.lastIndexOf("}");
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s.trim();
    }

    private int estimateTokens(String text) {
        return text != null ? text.length() / 2 : 0;
    }
}
