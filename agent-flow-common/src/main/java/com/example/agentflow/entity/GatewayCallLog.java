package com.example.agentflow.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("gateway_call_log")
public class GatewayCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long latencyMs;

    private Boolean success;

    private String errorMsg;

    private BigDecimal costEstimate;

    private LocalDateTime createdAt;
}
