package com.example.agentflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalResult {
    private String text;
    private String type;     // "vector", "graph", "fulltext"
    private String source;   // 文档名、实体名等
    private double score;
}
