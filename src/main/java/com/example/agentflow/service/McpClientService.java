package com.example.agentflow.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpClientService {

    private static final Logger mcpLog = LoggerFactory.getLogger("mcp.tool");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ToolRegistry toolRegistry;
    private final Map<String, McpServerConfig.ServerInfo> serverConfigs = new ConcurrentHashMap<>();

    @Bean
    @ConfigurationProperties(prefix = "mcp")
    public McpServerConfig mcpServerConfig() {
        return new McpServerConfig();
    }

    @Bean
    public Object initMcpConnections(McpServerConfig config) {
        if (config.getServers() == null) return null;

        for (McpServerConfig.ServerInfo server : config.getServers()) {
            serverConfigs.put(server.getName(), server);
            if (server.isEnabled()) {
                connectServer(server);
            }
        }
        return null;
    }

    private void connectServer(McpServerConfig.ServerInfo server) {
        try {
            HttpMcpTransport transport = HttpMcpTransport.builder()
                    .sseUrl(server.getEndpoint())
                    .timeout(READ_TIMEOUT)
                    .logRequests(true)
                    .logResponses(true)
                    .logger(mcpLog)
                    .build();

            McpClient client = DefaultMcpClient.builder()
                    .clientName(server.getName())
                    .transport(transport)
                    .toolExecutionTimeout(READ_TIMEOUT)
                    .autoHealthCheck(true)
                    .autoHealthCheckInterval(Duration.ofSeconds(30))
                    .build();

            toolRegistry.registerMcpClient(server.getName(), client);
            log.info("Connected MCP server [{}] at {}", server.getName(), server.getEndpoint());
        } catch (Exception e) {
            log.error("Failed to connect MCP server [{}]: {}", server.getName(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void healthCheck() {
        for (Map.Entry<String, McpServerConfig.ServerInfo> entry : serverConfigs.entrySet()) {
            String name = entry.getKey();
            McpServerConfig.ServerInfo server = entry.getValue();
            if (!server.isEnabled()) {
                toolRegistry.unregisterServer(name);
                continue;
            }
            try {
                McpClient client = null;
                // check if already registered
                ToolRegistry.ToolEntry tool = toolRegistry.getTool("mcp:" + name + ":");
                if (tool == null) {
                    connectServer(server);
                }
            } catch (Exception e) {
                log.warn("MCP server [{}] health check failed: {}", name, e.getMessage());
            }
        }
    }

    @Data
    public static class McpServerConfig {
        private List<ServerInfo> servers;

        @Data
        public static class ServerInfo {
            private String name;
            private String endpoint;
            private boolean enabled = true;
        }
    }
}
