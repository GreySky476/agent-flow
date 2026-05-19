package com.example.agentflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("wf_node")
public class WorkflowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long definitionId;

    private String nodeId;

    private String nodeType;

    private String configJson;

    private Integer positionX;

    private Integer positionY;

    private String nextNodes;
}
