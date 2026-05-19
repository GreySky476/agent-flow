package com.example.agentflow.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentflow.entity.WorkflowDefinition;
import com.example.agentflow.mapper.WorkflowDefinitionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowVersionService {

    private final WorkflowDefinitionMapper definitionMapper;

    public WorkflowDefinition publish(Long definitionId, boolean isPublic) {
        WorkflowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + definitionId);
        }

        int newVersion = (def.getVersion() != null ? def.getVersion() : 0) + 1;

        def.setStatus("PUBLISHED");
        def.setVersion(newVersion);
        def.setIsPublic(isPublic);
        def.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(def);

        log.info("Published workflow [{}] version [{}], id: [{}], isPublic: {}",
                def.getName(), newVersion, def.getId(), isPublic);

        return def;
    }

    public List<Map<String, Object>> listPublished() {
        List<WorkflowDefinition> published = definitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getStatus, "PUBLISHED")
                        .eq(WorkflowDefinition::getIsPublic, true)
                        .orderByDesc(WorkflowDefinition::getUpdatedAt));

        return published.stream().map(def -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", def.getId());
            item.put("name", def.getName());
            item.put("description", def.getDescription());
            item.put("version", def.getVersion());
            item.put("updatedAt", def.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());
    }

    public WorkflowDefinition updateIsPublic(Long definitionId, boolean isPublic) {
        WorkflowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new IllegalArgumentException("Workflow definition not found: " + definitionId);
        }
        def.setIsPublic(isPublic);
        def.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(def);
        return def;
    }
}
