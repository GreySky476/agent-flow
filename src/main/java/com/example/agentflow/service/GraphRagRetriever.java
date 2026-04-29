package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.RetrievalResult;

import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class GraphRagRetriever {

    private final HybridSearchService hybridSearchService;
    private final GraphStorageService graphStorageService;

    public List<RetrievalResult> retrieve(String query, int topK) {
        List<RetrievalResult> results = new ArrayList<>();

        // 1. 向量+全文检索
        try {
            List<TextSegment> segments = hybridSearchService.search(query, topK);
            for (int i = 0; i < segments.size(); i++) {
                TextSegment seg = segments.get(i);
                String docName = seg.metadata().getString("doc_name");
                results.add(RetrievalResult.builder()
                        .text(seg.text())
                        .type("vector")
                        .source(docName != null ? docName : "unknown")
                        .score(1.0 / (i + 1))
                        .build());
            }
            log.debug("Vector search: {} results", segments.size());
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
        }

        // 2. 图检索
        try {
            List<Map<String, Object>> entities = graphStorageService.searchRelevantEntities(query);
            if (!entities.isEmpty()) {
                String graphContext = buildGraphContext(entities);
                results.add(RetrievalResult.builder()
                        .text(graphContext)
                        .type("graph")
                        .source("knowledge-graph")
                        .score(0.8)
                        .build());
                log.debug("Graph search: {} entities", entities.size());
            }
        } catch (Exception e) {
            log.warn("Graph search failed: {}", e.getMessage());
        }

        // 3. 子图查询
        try {
            Map<String, Object> subgraph = graphStorageService.queryGraph(query);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) subgraph.getOrDefault("nodes", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) subgraph.getOrDefault("relations", List.of());
            if (!nodes.isEmpty()) {
                String subContext = buildSubgraphContext(nodes, edges);
                results.add(RetrievalResult.builder()
                        .text(subContext)
                        .type("graph")
                        .source("knowledge-graph-subgraph")
                        .score(0.7)
                        .build());
                log.debug("Subgraph search: {} nodes, {} edges", nodes.size(), edges.size());
            }
        } catch (Exception e) {
            log.warn("Subgraph query failed: {}", e.getMessage());
        }

        log.info("GraphRAG retrieve: {} total results for query [{}]", results.size(),
                query.substring(0, Math.min(50, query.length())));
        return results;
    }

    private String buildGraphContext(List<Map<String, Object>> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识图谱中的相关实体：\n");
        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> entity = entities.get(i);
            String id = String.valueOf(entity.getOrDefault("id", "unknown"));
            String type = String.valueOf(entity.getOrDefault("type", "Entity"));
            sb.append("  - [").append(type).append("] ").append(id);
            entity.forEach((k, v) -> {
                if (!k.equals("id") && !k.equals("type")) {
                    sb.append(", ").append(k).append("=").append(v);
                }
            });
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildSubgraphContext(List<Map<String, Object>> nodes,
                                         List<Map<String, Object>> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识图谱子图信息（两跳关联）：\n");
        sb.append("节点: ");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nodes.get(i).get("id"));
        }
        sb.append("\n关系:\n");
        for (Map<String, Object> edge : edges) {
            sb.append("  ").append(edge.get("from"))
                    .append(" -[").append(edge.get("type")).append("]-> ")
                    .append(edge.get("to")).append("\n");
        }
        return sb.toString();
    }
}
