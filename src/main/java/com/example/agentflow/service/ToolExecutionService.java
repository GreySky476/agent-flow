package com.example.agentflow.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.agentflow.service.ToolRegistry.ToolEntry;
import com.example.agentflow.service.ToolRegistry.ToolType;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

    private final ToolRegistry toolRegistry;

    public String execute(String toolName, Map<String, Object> parameters) {
        ToolEntry entry = toolRegistry.getTool(toolName);
        if (entry == null) {
            log.warn("Tool [{}] not found in registry", toolName);
            return "{\"error\": \"Tool not found: " + toolName + "\"}";
        }

        log.info("Executing tool [{}] type [{}] with params: {}", toolName, entry.getType(), parameters);

        return switch (entry.getType()) {
            case LOCAL -> executeLocal(entry, parameters);
            case MCP -> executeMcp(entry, parameters);
        };
    }

    private String executeLocal(ToolEntry entry, Map<String, Object> parameters) {
        Method method = entry.getMethod();
        Object bean = entry.getBean();
        try {
            Object result;
            if (method.getParameterCount() == 1) {
                Parameter param = method.getParameters()[0];
                Object arg = resolveParameter(param, parameters);
                result = method.invoke(bean, arg);
            } else if (method.getParameterCount() == 0) {
                result = method.invoke(bean);
            } else {
                // 多参数方法：根据参数名匹配
                Object[] args = new Object[method.getParameterCount()];
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Parameter param = method.getParameters()[i];
                    args[i] = resolveParameter(param, parameters);
                }
                result = method.invoke(bean, args);
            }

            String output = result != null ? result.toString() : "";
            log.info("Tool [{}] returned: {}", entry.getName(), output);
            return output;
        } catch (InvocationTargetException e) {
            log.error("Tool [{}] execution failed", entry.getName(), e.getCause());
            return "{\"error\": \"" + e.getCause().getMessage() + "\"}";
        } catch (Exception e) {
            log.error("Tool [{}] invocation failed", entry.getName(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executeMcp(ToolEntry entry, Map<String, Object> parameters) {
        McpClient client = (McpClient) entry.getBean();
        try {
            String arguments = toJsonString(parameters);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(entry.getName())
                    .arguments(arguments)
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            return String.valueOf(result.result());
        } catch (Exception e) {
            log.error("MCP tool [{}] execution failed", entry.getName(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private Object resolveParameter(Parameter param, Map<String, Object> parameters) {
        String paramName = param.getName();
        if (parameters.containsKey(paramName)) {
            return parameters.get(paramName);
        }
        if (parameters.containsKey("query")) {
            return parameters.get("query");
        }
        if (parameters.containsKey("input")) {
            return parameters.get("input");
        }
        for (Object value : parameters.values()) {
            if (value != null) return value;
        }
        return null;
    }

    private String toJsonString(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : params.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
