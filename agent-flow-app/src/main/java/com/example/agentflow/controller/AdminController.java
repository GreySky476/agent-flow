package com.example.agentflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agentflow.config.ModelRegistryConfig.ModelProperties;
import com.example.agentflow.entity.GatewayCallLog;
import com.example.agentflow.service.GatewayCallLogService;
import com.example.agentflow.service.HealthCheckService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ModelProperties modelProperties;
    private final HealthCheckService healthCheckService;
    private final GatewayCallLogService gatewayCallLogService;

    @GetMapping("/models")
    public List<ModelVO> listModels() {
        return modelProperties.getList().stream()
                .map(config -> new ModelVO(
                        config.getName(),
                        config.getType().name(),
                        healthCheckService.isHealthy(config.getName()),
                        config.getCostTier().name()))
                .toList();
    }

    @PostMapping("/models/{name}/enable")
    public void enableModel(@PathVariable String name) {
        healthCheckService.markHealthy(name);
    }

    @PostMapping("/models/{name}/disable")
    public void disableModel(@PathVariable String name) {
        healthCheckService.markUnhealthy(name);
    }

    @GetMapping("/call-logs")
    public IPage<GatewayCallLog> listCallLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var wrapper = new LambdaQueryWrapper<GatewayCallLog>()
                .orderByDesc(GatewayCallLog::getId);
        return gatewayCallLogService.page(Page.of(page, size), wrapper);
    }

    public record ModelVO(String name, String type, boolean healthy, String costTier) {}
}
