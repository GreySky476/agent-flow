package com.example.agentflow.model;

import java.util.List;

import lombok.Data;

@Data
public class AgentConfig {
    private String name;
    private String systemPrompt;
    private List<String> toolNames;
    private String modelPreference;
}
