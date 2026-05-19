package com.example.agentflow.service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.agentflow.annotation.AgentTool;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    void scanLocalTools() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);
        beans.putAll(applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Service.class));

        for (Object bean : beans.values()) {
            Class<?> clazz = getRealClass(bean);
            for (Method method : clazz.getDeclaredMethods()) {
                AgentTool annotation = method.getAnnotation(AgentTool.class);
                if (annotation == null) continue;

                String toolName = annotation.name();
                List<String> params = Arrays.stream(method.getParameters())
                        .map(Parameter::getName)
                        .toList();

                tools.put(toolName, new ToolEntry(toolName, annotation.description(),
                        ToolType.LOCAL, "local", bean, method,
                        annotation.exportable(), true, params));
                log.info("Registered local tool [{}] → {}.{}", toolName, clazz.getSimpleName(), method.getName());
            }
        }
    }

    public void registerMcpClient(String serverName, Object client) {
        // MCP removed in feature-v1
    }

    public void registerMcpTool(String serverName, String toolName, Object toolInstance) {
        // MCP removed in feature-v1
    }

    public void unregisterMcpTool(String toolName) {
        tools.remove(toolName);
        log.info("Unregistered tool [{}]", toolName);
    }

    public void unregisterServer(String serverName) {
        String prefix = "mcp:" + serverName + ":";
        tools.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        log.info("Unregistered MCP server [{}] and its tools", serverName);
    }

    public void enableTool(String toolName) {
        ToolEntry entry = tools.get(toolName);
        if (entry != null) {
            entry.setEnabled(true);
            log.info("Tool [{}] enabled", toolName);
        }
    }

    public void disableTool(String toolName) {
        ToolEntry entry = tools.get(toolName);
        if (entry != null) {
            entry.setEnabled(false);
            log.info("Tool [{}] disabled", toolName);
        }
    }

    public List<Map<String, Object>> listAllTools() {
        return tools.entrySet().stream()
                .map(e -> {
                    ToolEntry t = e.getValue();
                    return Map.<String, Object>of(
                            "name", e.getKey(),
                            "description", t.getDescription(),
                            "source", t.getSource(),
                            "type", t.getType().name(),
                            "params", t.getParams(),
                            "enabled", t.isEnabled(),
                            "exportable", t.isExportable());
                })
                .collect(Collectors.toList());
    }

    public ToolEntry getTool(String toolName) {
        ToolEntry entry = tools.get(toolName);
        return entry != null && entry.isEnabled() ? entry : null;
    }

    public Map<String, ToolEntry> getToolMap() {
        return Map.copyOf(tools);
    }

    public Object getToolInstance(String toolName) {
        ToolEntry entry = getTool(toolName);
        return entry != null ? entry.getBean() : null;
    }

    public Object[] getTools(String... toolNames) {
        if (toolNames == null || toolNames.length == 0) return new Object[0];
        return Arrays.stream(toolNames)
                .map(this::getToolInstance)
                .filter(obj -> obj != null)
                .toArray();
    }

    private static String buildMcpToolName(String serverName, String toolName) {
        return "mcp:" + serverName + ":" + toolName;
    }

    private Class<?> getRealClass(Object bean) {
        Class<?> clazz = bean.getClass();
        while (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }
        return clazz;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolEntry {
        private final String name;
        private final String description;
        private final ToolType type;
        private final String source;
        private final Object bean;
        private final Method method;
        private final boolean exportable;
        private boolean enabled;
        private final List<String> params;
    }

    public enum ToolType { LOCAL, MCP }
}
