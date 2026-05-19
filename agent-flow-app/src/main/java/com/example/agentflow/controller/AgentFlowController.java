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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agentflow.entity.WorkflowDefinition;
import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.mapper.WorkflowDefinitionMapper;
import com.example.agentflow.mapper.WorkflowNodeMapper;
import com.example.agentflow.service.ToolRegistry;
import com.example.agentflow.service.WorkflowExecutor;
import com.example.agentflow.service.WorkflowVersionService;

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
    private final WorkflowVersionService versionService;
    private final ToolRegistry toolRegistry;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/definitions")
    public Map<String, Object> listDefinitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(WorkflowDefinition::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(WorkflowDefinition::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(WorkflowDefinition::getUpdatedAt);

        Page<WorkflowDefinition> result = definitionMapper.selectPage(Page.of(page, size), wrapper);

        List<Map<String, Object>> items = result.getRecords().stream().map(def -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", def.getId());
            item.put("name", def.getName());
            item.put("description", def.getDescription());
            item.put("status", def.getStatus());
            item.put("version", def.getVersion());
            item.put("isPublic", def.getIsPublic());
            item.put("createdAt", def.getCreatedAt());
            item.put("updatedAt", def.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());

        return Map.of(
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "size", result.getSize(),
                "items", items);
    }

    @PostMapping("/definitions")
    public Map<String, Object> saveDefinition(@RequestBody DefinitionRequest request) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(request.id());
        def.setName(request.name());
        def.setDescription(request.description());
        def.setStatus(request.status() != null ? request.status() : "DRAFT");
        def.setDefinitionJson(request.definitionJson());
        def.setInputSchema(request.inputSchema());
        def.setOutputSchema(request.outputSchema());
        def.setUpdatedAt(LocalDateTime.now());

        if (def.getId() != null) {
            definitionMapper.updateById(def);
            nodeMapper.delete(new LambdaQueryWrapper<WorkflowNode>()
                    .eq(WorkflowNode::getDefinitionId, def.getId()));
        } else {
            def.setVersion(1);
            def.setIsPublic(false);
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
        result.put("version", def.getVersion());
        result.put("isPublic", def.getIsPublic());
        result.put("inputSchema", def.getInputSchema());
        result.put("outputSchema", def.getOutputSchema());
        result.put("definitionJson", def.getDefinitionJson());
        result.put("nodes", nodes);
        result.put("createdAt", def.getCreatedAt());
        result.put("updatedAt", def.getUpdatedAt());
        return result;
    }

    @DeleteMapping("/definitions/{id}")
    public Map<String, Object> deleteDefinition(@PathVariable Long id) {
        WorkflowDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("Definition not found: " + id);
        }
        def.setStatus("ARCHIVED");
        def.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(def);
        return Map.of("success", true);
    }

    @PutMapping("/definitions/{id}/publish")
    public Map<String, Object> publishDefinition(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        boolean isPublic = body.get("isPublic") instanceof Boolean b ? b : false;
        WorkflowDefinition published = versionService.publish(id, isPublic);
        return Map.of(
                "success", true,
                "id", published.getId(),
                "version", published.getVersion(),
                "status", published.getStatus());
    }

    @GetMapping("/definitions/published")
    public List<Map<String, Object>> listPublishedDefinitions() {
        return versionService.listPublished();
    }

    @PutMapping("/definitions/{id}/public")
    public Map<String, Object> updatePublic(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        boolean isPublic = body.get("isPublic") instanceof Boolean b ? b : false;
        WorkflowDefinition def = versionService.updateIsPublic(id, isPublic);
        return Map.of("success", true, "isPublic", def.getIsPublic());
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

    @PostMapping("/chat/{definitionId}")
    public Map<String, Object> chat(@PathVariable Long definitionId,
                                     @RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        String conversationId = (String) body.getOrDefault("conversationId", "");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userInput", message);
        if (!conversationId.isEmpty()) {
            params.put("conversationId", conversationId);
        }

        Map<String, Object> result = workflowExecutor.execute(definitionId, params);
        String instanceId = (String) result.get("_instanceId");

        String response = result.values().stream()
                .filter(v -> v instanceof String && ((String) v).length() > 0)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        try {
            redisTemplate.opsForValue().set("wf:meta:" + instanceId,
                    objectMapper.writeValueAsString(Map.of("status", "COMPLETED", "result", result)),
                    Duration.ofHours(1));
        } catch (Exception ignored) {}

        return Map.of(
                "success", true,
                "instanceId", instanceId,
                "response", response,
                "conversationId", conversationId.isEmpty() ? instanceId : conversationId);
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
            String definitionJson, String inputSchema, String outputSchema,
            List<NodeRequest> nodes) {}

    public record NodeRequest(
            String nodeId, String nodeType, String configJson,
            Integer positionX, Integer positionY, String nextNodes) {}
}
