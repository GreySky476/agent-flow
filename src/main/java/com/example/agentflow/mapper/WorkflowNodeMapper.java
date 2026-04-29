package com.example.agentflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.agentflow.entity.WorkflowNode;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}
