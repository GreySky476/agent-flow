package com.example.agentflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequest {
    private String preferredModel;
    private String message;
}
