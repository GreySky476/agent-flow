package com.example.agentflow.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import com.agentflow.gateway.agentflow.core.AgentResponse;
import com.agentflow.gateway.agentflow.core.ReasoningRouter;
import com.agentflow.gateway.memory.SmartMemoryService;
import com.example.agentflow.entity.WorkflowDefinition;
import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.mapper.WorkflowDefinitionMapper;
import com.example.agentflow.mapper.WorkflowNodeMapper;
import com.example.agentflow.model.AgentConfig;
import com.example.agentflow.model.WorkflowState;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

    private static final Duration NODE_TIMEOUT = Duration.ofSeconds(120);
    private static final String REDIS_KEY_PREFIX = "wf:state:";

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final ReasoningRouter reasoningRouter;
    private final AgentRegistry agentRegistry;
    private final ToolExecutionService toolExecutionService;
    private final SmartMemoryService smartMemoryService;
    private final StringRedisTemplate redisTemplate;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public Map<String, Object> execute(Long definitionId, Map<String, Object> initialInput) {
        String instanceId = UUID.randomUUID().toString().substring(0, 8);

        WorkflowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + definitionId);
        }

        List<WorkflowNode> nodes = nodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getDefinitionId, definitionId));

        if (nodes.isEmpty()) {
            throw new IllegalStateException("No nodes found for definition: " + definitionId);
        }

        log.info("Starting workflow [{}] instance [{}] with {} nodes",
                definition.getName(), instanceId, nodes.size());

        WorkflowState state = new WorkflowState().merge(initialInput);
        state.put("_instanceId", instanceId);
        state.put("_definitionName", definition.getName());

        // 构建拓扑结构
        Map<String, WorkflowNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(WorkflowNode::getNodeId, n -> n));
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new ConcurrentHashMap<>();

        for (WorkflowNode node : nodes) {
            adjacency.putIfAbsent(node.getNodeId(), new ArrayList<>());
            inDegree.putIfAbsent(node.getNodeId(), 0);
            if (node.getNextNodes() != null && !node.getNextNodes().isBlank()) {
                for (String next : node.getNextNodes().split(",")) {
                    next = next.trim();
                    if (!next.isEmpty()) {
                        adjacency.get(node.getNodeId()).add(next);
                        inDegree.merge(next, 1, Integer::sum);
                        inDegree.putIfAbsent(next, 0);
                    }
                }
            }
        }

        // Kahn 拓扑排序，按层级分组
        List<List<String>> levels = kahnLevels(adjacency, inDegree);

        // 按层级顺序执行
        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            List<String> currentLevel = levels.get(levelIdx);
            log.info("Executing level {}/{}: {}", levelIdx + 1, levels.size(), currentLevel);

            List<CompletableFuture<Void>> futures = currentLevel.stream()
                    .map(nodeId -> CompletableFuture.runAsync(() -> {
                        WorkflowNode node = nodeMap.get(nodeId);
                        if (node == null) return;
                        try {
                            executeNode(node, state, instanceId);
                        } catch (Exception e) {
                            log.error("Node [{}] execution failed", nodeId, e);
                            throw new RuntimeException("Node [" + nodeId + "] failed: " + e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(NODE_TIMEOUT.toMillis() * currentLevel.size(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("Workflow level " + levelIdx + " timed out", e);
            } catch (Exception e) {
                throw new RuntimeException("Workflow execution failed at level " + levelIdx, e);
            }
        }

        log.info("Workflow [{}] instance [{}] completed", definition.getName(), instanceId);
        return state.getVariables();
    }

    private void executeNode(WorkflowNode node, WorkflowState state, String instanceId) {
        log.debug("Executing node [{}] type [{}]", node.getNodeId(), node.getNodeType());

        switch (node.getNodeType().toUpperCase()) {
            case "START" -> {
                state.put("_started", true);
            }
            case "END" -> {
                state.put("_completed", true);
            }
            case "AGENT" -> {
                String agentName = extractConfigValue(node.getConfigJson(), "agentName");
                AgentConfig config = agentRegistry.getAgentConfig(agentName);
                if (config == null) {
                    config = new AgentConfig();
                    config.setName(agentName != null ? agentName : "default");
                    config.setSystemPrompt("你是一个智能助手");
                }

                String sessionId = String.valueOf(state.get("conversationId") != null
                        ? state.get("conversationId") : state.get("_instanceId"));

                String memoryContext = smartMemoryService.getContextForLLM(sessionId);
                if (!memoryContext.isBlank()) {
                    config.setSystemPrompt(config.getSystemPrompt() + "\n\n## 历史记忆\n" + memoryContext);
                }

                String modeOverride = extractConfigValue(node.getConfigJson(), "mode");
                if (modeOverride != null) {
                    state.put("modeOverride", modeOverride);
                }
                String userQuery = String.valueOf(state.get("userQuery") != null
                        ? state.get("userQuery") : extractConfigValue(node.getConfigJson(), "prompt"));
                state.put("task", userQuery);

                smartMemoryService.add(sessionId, "user", userQuery);

                AgentResponse response = reasoningRouter.route(state);

                if (response.getOutput() != null) {
                    state.put("_agent_" + config.getName() + "_response", response.getOutput());
                    state.put("_agent_output", response.getOutput());
                }
                if (!response.getIntermediateSteps().isEmpty()) {
                    state.put("_agent_steps", response.getIntermediateSteps());
                }
                if (!response.getToolCalls().isEmpty()) {
                    state.put("_agent_tool_calls", response.getToolCalls());
                }
                if (response.getError() != null) {
                    state.put("_agent_error", response.getError());
                }

                if (response.getOutput() != null) {
                    smartMemoryService.add(sessionId, "assistant", response.getOutput());
                }
            }
            case "TOOL" -> {
                String toolName = extractConfigValue(node.getConfigJson(), "toolName");
                toolExecutionService.execute(toolName, state.getVariables());
            }
            case "CONDITION" -> {
                evaluateCondition(node, state);
            }
            default -> {
                log.warn("Unknown node type: {}", node.getNodeType());
            }
        }

        state.put("_lastNodeId", node.getNodeId());
        persistState(instanceId, node.getNodeId(), state);
    }

    private void evaluateCondition(WorkflowNode node, WorkflowState state) {
        String expression = extractConfigValue(node.getConfigJson(), "expression");
        if (expression == null || expression.isBlank()) {
            return;
        }
        StandardEvaluationContext context = new StandardEvaluationContext(state.getVariables());
        try {
            Boolean result = spelParser.parseExpression(expression).getValue(context, Boolean.class);
            state.put("_condition_" + node.getNodeId(), result);
            log.info("Condition [{}] evaluated to: {}", node.getNodeId(), result);
        } catch (Exception e) {
            log.error("Failed to evaluate condition [{}]: {}", node.getNodeId(), e.getMessage());
            state.put("_condition_" + node.getNodeId(), false);
        }
    }

    private String extractConfigValue(String configJson, String key) {
        if (configJson == null || configJson.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(configJson);
            if (node.has(key)) {
                return node.get(key).asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<List<String>> kahnLevels(
            Map<String, List<String>> adjacency,
            Map<String, Integer> inDegree) {

        List<List<String>> levels = new ArrayList<>();
        Map<String, Integer> workingDegree = new HashMap<>(inDegree);

        while (true) {
            List<String> currentLevel = workingDegree.entrySet().stream()
                    .filter(e -> e.getValue() == 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();

            if (currentLevel.isEmpty()) break;
            levels.add(currentLevel);

            for (String nodeId : currentLevel) {
                workingDegree.remove(nodeId);
                for (String next : adjacency.getOrDefault(nodeId, List.of())) {
                    workingDegree.merge(next, -1, (a, b) -> a - 1 < 0 ? 0 : a - 1);
                }
            }
        }

        if (!workingDegree.isEmpty()) {
            log.warn("Graph contains a cycle, remaining nodes: {}", workingDegree.keySet());
        }
        return levels;
    }

    private void persistState(String instanceId, String nodeId, WorkflowState state) {
        try {
            String key = REDIS_KEY_PREFIX + instanceId + ":" + nodeId;
            redisTemplate.opsForValue().set(key, state.toJson(), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to persist state to Redis: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
