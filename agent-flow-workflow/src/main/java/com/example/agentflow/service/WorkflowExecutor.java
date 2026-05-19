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
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentflow.entity.WorkflowDefinition;
import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.mapper.WorkflowDefinitionMapper;
import com.example.agentflow.mapper.WorkflowNodeMapper;
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
    private final LlmNodeExecutor llmNodeExecutor;
    private final BranchEvaluator branchEvaluator;
    private final StringRedisTemplate redisTemplate;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Map<String, Object> execute(Long definitionId, Map<String, Object> initialInput) {
        String instanceId = UUID.randomUUID().toString().substring(0, 8);

        WorkflowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + definitionId);
        }

        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getDefinitionId, definitionId));

        if (nodes.isEmpty()) {
            throw new IllegalStateException("No nodes found for definition: " + definitionId);
        }

        log.info("Starting workflow [{}] instance [{}] with {} nodes",
                definition.getName(), instanceId, nodes.size());

        WorkflowState state = new WorkflowState().merge(initialInput);
        state.put("_instanceId", instanceId);
        state.put("_definitionName", definition.getName());

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

        List<List<String>> levels = kahnLevels(adjacency, inDegree);

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
        return state.getData();
    }

    private void executeNode(WorkflowNode node, WorkflowState state, String instanceId) {
        log.debug("Executing node [{}] type [{}]", node.getNodeId(), node.getNodeType());

        switch (node.getNodeType().toUpperCase()) {
            case "START" -> state.put("_started", true);
            case "END" -> state.put("_completed", true);
            case "LLM" -> llmNodeExecutor.execute(node, state);
            case "BRANCH", "CONDITION" -> evaluateBranch(node, state);
            default -> log.warn("Unknown node type: {}", node.getNodeType());
        }

        state.put("_lastNodeId", node.getNodeId());
        persistState(instanceId, node.getNodeId(), state);
    }

    private void evaluateBranch(WorkflowNode node, WorkflowState state) {
        String result = branchEvaluator.evaluate(node, state);
        log.debug("Branch [{}] result: {}", node.getNodeId(), result);
    }

    private List<List<String>> kahnLevels(Map<String, List<String>> adjacency, Map<String, Integer> inDegree) {
        List<List<String>> levels = new ArrayList<>();
        Map<String, Integer> workingDegree = new HashMap<>(inDegree);
        while (true) {
            List<String> currentLevel = workingDegree.entrySet().stream()
                    .filter(e -> e.getValue() == 0).map(Map.Entry::getKey).sorted().toList();
            if (currentLevel.isEmpty()) break;
            levels.add(currentLevel);
            for (String nodeId : currentLevel) {
                workingDegree.remove(nodeId);
                for (String next : adjacency.getOrDefault(nodeId, List.of())) {
                    workingDegree.merge(next, -1, (a, b) -> a - 1 < 0 ? 0 : a - 1);
                }
            }
        }
        if (!workingDegree.isEmpty()) log.warn("Graph contains a cycle: {}", workingDegree.keySet());
        return levels;
    }

    private void persistState(String instanceId, String nodeId, WorkflowState state) {
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + instanceId + ":" + nodeId,
                    state.toJson(), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to persist state: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
