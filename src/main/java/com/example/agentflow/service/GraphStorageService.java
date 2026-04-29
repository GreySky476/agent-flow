package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.ChatRequest;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphStorageService {

    private final org.neo4j.driver.Driver neo4jDriver;
    private final SmartChatRouter smartChatRouter;

    public void mergeGraph(Map<String, Object> graph) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) graph.getOrDefault("entities", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) graph.getOrDefault("relations", List.of());

        log.info("Merging {} entities and {} relations into Neo4j", entities.size(), relations.size());

        try (var session = neo4jDriver.session()) {
            // 批量合并实体
            for (Map<String, Object> entity : entities) {
                String id = String.valueOf(entity.get("id"));
                String type = String.valueOf(entity.getOrDefault("type", "Entity"));
                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) entity.getOrDefault("properties", Map.of());

                session.executeWrite(tx -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("id", id);
                    params.put("type", type);
                    params.put("props", props);
                    tx.run("""
                            MERGE (n:Entity {id: $id})
                            SET n.type = $type, n += $props
                            """, params);
                    return null;
                });
            }

            // 批量合并关系
            for (Map<String, Object> rel : relations) {
                String from = String.valueOf(rel.get("from"));
                String to = String.valueOf(rel.get("to"));
                String type = String.valueOf(rel.getOrDefault("type", "RELATED_TO"));
                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) rel.getOrDefault("properties", Map.of());

                session.executeWrite(tx -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("from", from);
                    params.put("to", to);
                    params.put("type", type);
                    params.put("props", props);
                    tx.run("""
                            MATCH (a:Entity {id: $from})
                            MATCH (b:Entity {id: $to})
                            MERGE (a)-[r:%s]->(b)
                            SET r += $props
                            """.formatted(type.replaceAll("[^a-zA-Z0-9_]", "_")), params);
                    return null;
                });
            }
        }
        log.info("Graph merge completed");
    }

    public Map<String, Object> queryGraph(String query) {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) return Map.of("nodes", List.of(), "relations", List.of());

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (var session = neo4jDriver.session()) {
            String cypher = """
                    MATCH (n:Entity)
                    WHERE """ + buildKeywordFilter(keywords) + """
                    WITH n
                    OPTIONAL MATCH (n)-[r]-(m:Entity)
                    WHERE r IS NOT NULL
                    RETURN n, r, m
                    LIMIT 50
                    """;

            var result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                nodes.add(nodeToMap(record.get("n")));
                if (record.get("m") != null && !record.get("m").isNull()) {
                    nodes.add(nodeToMap(record.get("m")));
                }
                if (record.get("r") != null && !record.get("r").isNull()) {
                    edges.add(relToMap(record.get("r"), record.get("n"), record.get("m")));
                }
            }
        }

        // 去重节点
        List<Map<String, Object>> deduped = new ArrayList<>();
        var seen = new java.util.HashSet<String>();
        for (var node : nodes) {
            String id = String.valueOf(node.get("id"));
            if (seen.add(id)) deduped.add(node);
        }

        return Map.of("nodes", deduped, "relations", edges);
    }

    public List<Map<String, Object>> searchRelevantEntities(String text) {
        List<String> keywords = extractKeywords(text);
        if (keywords.isEmpty()) return List.of();

        List<Map<String, Object>> results = new ArrayList<>();
        try (var session = neo4jDriver.session()) {
            String filter = buildKeywordFilter(keywords);
            var cypherResult = session.run("""
                    MATCH (n:Entity)
                    WHERE """ + filter + """
                    RETURN n LIMIT 20
                    """);
            while (cypherResult.hasNext()) {
                results.add(nodeToMap(cypherResult.next().get("n")));
            }
        }
        return results;
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();

        try {
            ChatModel model = smartChatRouter.route(ChatRequest.builder().message(text).build());
            String response = model.chat("从以下文本中提取关键词（实体），用逗号分隔，最多10个: " + text);
            String[] parts = response.split("[,，;；\n]+");
            List<String> keywords = new ArrayList<>();
            for (String part : parts) {
                String kw = part.trim().replaceAll("[^\\p{L}\\p{N}]", "");
                if (kw.length() >= 2 && kw.length() <= 50) {
                    keywords.add(kw);
                }
            }
            if (keywords.isEmpty()) {
                keywords = extractKeywordsFallback(text);
            }
            return keywords;
        } catch (Exception e) {
            return extractKeywordsFallback(text);
        }
    }

    private List<String> extractKeywordsFallback(String text) {
        // 简单按空格和标点分割取名词性短语
        String[] words = text.split("[\\s,，。.！!？?；;：:]+");
        List<String> keywords = new ArrayList<>();
        for (String w : words) {
            String cleaned = w.trim().replaceAll("[^\\p{L}\\p{N}]", "");
            if (cleaned.length() >= 2) keywords.add(cleaned);
            if (keywords.size() >= 10) break;
        }
        return keywords;
    }

    private String buildKeywordFilter(List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("n.id CONTAINS '").append(keywords.get(i).replace("'", "\\'")).append("'");
            sb.append(" OR n.type CONTAINS '").append(keywords.get(i).replace("'", "\\'")).append("'");
        }
        return sb.toString();
    }

    private Map<String, Object> nodeToMap(Value node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.get("id").asString());
        map.put("type", node.get("type").asString());
        for (String key : node.keys()) {
            if (!key.equals("id") && !key.equals("type")) {
                map.put(key, node.get(key).asObject());
            }
        }
        return map;
    }

    private Map<String, Object> relToMap(Value rel, Value from, Value to) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", rel.type());
        map.put("from", from.get("id").asString());
        map.put("to", to.get("id").asString());
        for (var entry : rel.asMap().entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
