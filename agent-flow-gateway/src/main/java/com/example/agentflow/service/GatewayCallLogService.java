package com.example.agentflow.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agentflow.entity.GatewayCallLog;
import com.example.agentflow.mapper.GatewayCallLogMapper;

import org.springframework.stereotype.Service;

@Service
public class GatewayCallLogService extends ServiceImpl<GatewayCallLogMapper, GatewayCallLog> {
}
