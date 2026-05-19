package com.example.agentflow.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentflow.entity.WorkflowDefinition;
import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.mapper.WorkflowDefinitionMapper;
import com.example.agentflow.mapper.WorkflowNodeMapper;
import com.example.agentflow.service.ToolRegistry;
import com.example.agentflow.service.WorkflowExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/agentflow")
@RequiredArgsConstructor
public class AgentFlowController {

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowExecutor workflowExecutor;
    private final ToolRegistry toolRegistry;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/definitions")
    public Map<String, Object> saveDefinition(@RequestBody DefinitionRequest request) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(request.id());
        def.setName(request.name());
        def.setDescription(request.description());
        def.setStatus(request.status() != null ? request.status() : "DRAFT");
        def.setDefinitionJson(request.definitionJson());
        def.setUpdatedAt(LocalDateTime.now());

        if (def.getId() != null) {
            definitionMapper.updateById(def);
            nodeMapper.delete(new LambdaQueryWrapper<WorkflowNode>()
                    .eq(WorkflowNode::getDefinitionId, def.getId()));
        } else {
            def.setCreatedAt(LocalDateTime.now());
            definitionMapper.insert(def);
        }

        if (request.nodes() != null) {
            for (NodeRequest nodeReq : request.nodes()) {
                WorkflowNode node = new WorkflowNode();
                node.setDefinitionId(def.getId());
                node.setNodeId(nodeReq.nodeId());
                node.setNodeType(nodeReq.nodeType());
                node.setConfigJson(nodeReq.configJson());
                node.setPositionX(nodeReq.positionX());
                node.setPositionY(nodeReq.positionY());
                node.setNextNodes(nodeReq.nextNodes());
                nodeMapper.insert(node);
            }
        }

        return Map.of("success", true, "id", def.getId());
    }

    @GetMapping("/definitions/{id}")
    public Map<String, Object> getDefinition(@PathVariable Long id) {
        WorkflowDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("Definition not found: " + id);
        }
        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getDefinitionId, id));

        Map<String, Object> result = new HashMap<>();
        result.put("id", def.getId());
        result.put("name", def.getName());
        result.put("description", def.getDescription());
        result.put("status", def.getStatus());
        result.put("definitionJson", def.getDefinitionJson());
        result.put("nodes", nodes);
        return result;
    }

    @PostMapping("/execute/{definitionId}")
    public Map<String, Object> execute(@PathVariable Long definitionId,
                                        @RequestBody Map<String, Object> params) {
        Map<String, Object> result = workflowExecutor.execute(definitionId, params);
        String instanceId = (String) result.get("_instanceId");

        try {
            redisTemplate.opsForValue().set("wf:meta:" + instanceId,
                    objectMapper.writeValueAsString(Map.of("status", "COMPLETED", "result", result)),
                    Duration.ofHours(1));
        } catch (Exception ignored) {}

        return Map.of("success", true, "instanceId", instanceId, "result", result);
    }

    @GetMapping("/tools")
    public List<Map<String, String>> listTools() {
        return toolRegistry.getToolMap().values().stream()
                .map(entry -> Map.of(
                        "name", entry.getName(),
                        "description", entry.getDescription(),
                        "type", entry.getType().name()))
                .collect(Collectors.toList());
    }

    @GetMapping("/status/{instanceId}")
    public Map<String, Object> getStatus(@PathVariable String instanceId) {
        String json = redisTemplate.opsForValue().get("wf:meta:" + instanceId);
        if (json != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = objectMapper.readValue(json, Map.class);
                return Map.of(
                        "instanceId", instanceId,
                        "status", meta.getOrDefault("status", "UNKNOWN"),
                        "result", meta.getOrDefault("result", ""));
            } catch (Exception ignored) {}
        }
        // 扫描所有节点状态
        List<String> nodeStatuses = new ArrayList<>();
        var keys = redisTemplate.keys("wf:state:" + instanceId + ":*");
        if (keys != null) {
            for (String key : keys) {
                String nodeId = key.substring(key.lastIndexOf(":") + 1);
                nodeStatuses.add(nodeId);
            }
        }
        return Map.of(
                "instanceId", instanceId,
                "status", "RUNNING",
                "completedNodes", nodeStatuses);
    }

    public record DefinitionRequest(
            Long id, String name, String description, String status,
            String definitionJson, List<NodeRequest> nodes) {}

    public record NodeRequest(
            String nodeId, String nodeType, String configJson,
            Integer positionX, Integer positionY, String nextNodes) {}
}
