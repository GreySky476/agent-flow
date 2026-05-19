package com.example.agentflow.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("wf_definition")
public class WorkflowDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String status;

    private Integer version;

    private Boolean isPublic;

    private String inputSchema;

    private String outputSchema;

    private String definitionJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
