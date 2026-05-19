package com.example.agentflow.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
@RequiredArgsConstructor
public class HybridSearchService {

    private static final double RRF_K = 60.0;
    private static final String TABLE_NAME = "rag_embeddings";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<TextSegment> search(String query, int maxResults) {
        // 1. 向量化查询
        Response<Embedding> queryResponse = embeddingModel.embed(query);
        Embedding queryEmbedding = queryResponse.content();

        // 2. 向量检索
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults * 2)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> vectorResults = searchResult.matches();

        // 3. 全文检索
        List<TextSegment> textResults = fullTextSearch(query, maxResults * 2);

        // 4. RRF 融合
        return rrfMerge(vectorResults, textResults, maxResults);
    }

    private List<TextSegment> fullTextSearch(String query, int limit) {
        String sql = """
                SELECT text, metadata
                FROM %s
                WHERE to_tsvector('english', text) @@ plainto_tsquery('english', ?)
                LIMIT ?
                """.formatted(TABLE_NAME);

        return jdbcTemplate.query(sql, rs -> {
            List<TextSegment> results = new ArrayList<>();
            while (rs.next()) {
                String text = rs.getString("text");
                String metadataJson = rs.getString("metadata");
                Metadata metadata = parseMetadata(metadataJson);
                results.add(TextSegment.from(text, metadata));
            }
            return results;
        }, query, limit);
    }

    private Metadata parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new Metadata();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(
                    metadataJson, new TypeReference<Map<String, Object>>() {});
            return Metadata.from(map);
        } catch (Exception e) {
            return new Metadata();
        }
    }

    private List<TextSegment> rrfMerge(
            List<EmbeddingMatch<TextSegment>> vectorResults,
            List<TextSegment> textResults,
            int maxResults) {

        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, TextSegment> segmentMap = new HashMap<>();

        // 向量检索结果
        for (int i = 0; i < vectorResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorResults.get(i);
            String id = match.embeddingId();
            TextSegment segment = match.embedded();
            segmentMap.put(id, segment);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(id, score, Double::sum);
        }

        // 全文检索结果
        for (int i = 0; i < textResults.size(); i++) {
            TextSegment segment = textResults.get(i);
            String text = segment.text();
            // 用文本的 hash 作为全文检索结果的 id
            String id = "fts_" + text.hashCode();
            segmentMap.put(id, segment);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(id, score, Double::sum);
        }

        // 按 RRF 分数排序取前 N 个
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(e -> segmentMap.get(e.getKey()))
                .toList();
    }
}
