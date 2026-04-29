package com.example.agentflow.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayChatRequest {
    private String message;
    private String conversationId;
    private String preferredModel;
}
