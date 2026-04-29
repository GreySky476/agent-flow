package com.agentflow.gateway.memory;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("memory_entities")
public class MemoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String entityName;
    private String entityValue;
    private LocalDateTime updatedAt;
}
