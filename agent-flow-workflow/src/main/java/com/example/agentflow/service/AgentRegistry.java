package com.example.agentflow.service;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.agentflow.model.AgentConfig;

import lombok.Data;

@Configuration
public class AgentRegistry {

    @Bean
    @ConfigurationProperties(prefix = "agentflow.agents")
    public AgentListProperties agentListProperties() {
        return new AgentListProperties();
    }

    public AgentConfig getAgentConfig(String agentName) {
        AgentListProperties properties = agentListProperties();
        if (properties.getList() == null) return null;
        return properties.getList().stream()
                .filter(a -> a.getName().equals(agentName))
                .findFirst()
                .orElse(null);
    }

    @Data
    public static class AgentListProperties {
        private List<AgentConfig> list;
    }
}
