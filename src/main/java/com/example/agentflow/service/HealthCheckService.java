package com.example.agentflow.service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class HealthCheckService {

    private final Set<String> unhealthyModels = ConcurrentHashMap.newKeySet();

    public void markUnhealthy(String modelName) {
        unhealthyModels.add(modelName);
    }

    public void markHealthy(String modelName) {
        unhealthyModels.remove(modelName);
    }

    public boolean isHealthy(String modelName) {
        return !unhealthyModels.contains(modelName);
    }

    public Set<String> getUnhealthyModels() {
        return Collections.unmodifiableSet(unhealthyModels);
    }
}
