package com.example.agentflow.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaResponse {
    private String answer;
    private List<SourceChunk> sources;

    @Data
    @Builder
    public static class SourceChunk {
        private String text;
        private String docName;
        private int chunkIndex;
    }
}
