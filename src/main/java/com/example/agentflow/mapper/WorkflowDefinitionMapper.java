package com.example.agentflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agentflow.entity.WorkflowDefinition;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {
}
