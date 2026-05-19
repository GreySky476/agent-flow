package com.example.agentflow.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.agentflow.model.ChatRequest;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientChatService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final SmartChatRouter smartChatRouter;
    private final HealthCheckService healthCheckService;
    private final Map<String, ChatModel> modelMap;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final ThreadLocal<String> lastModelName = new ThreadLocal<>();

    @Value("${resilience4j.timelimiter.configs.default.timeout-duration:30s}")
    private Duration timeoutDuration;

    public String chat(String message) {
        return chat(ChatRequest.builder().message(message).build());
    }

    public String chat(String message, String preferredModel) {
        return chat(ChatRequest.builder().preferredModel(preferredModel).message(message).build());
    }

    public String chat(ChatRequest request) {
        int maxAttempts = modelMap.size();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            ChatModel model = smartChatRouter.route(request);
            String modelName = findModelName(model);

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(modelName);

            try {
                String result = circuitBreaker.executeSupplier(
                        () -> callWithTimeout(model, request.getMessage()));
                healthCheckService.markHealthy(modelName);
                lastModelName.set(modelName);
                return result;
            } catch (CallNotPermittedException e) {
                log.warn("Circuit breaker OPEN for model [{}], trying next", modelName);
                healthCheckService.markUnhealthy(modelName);
                request.setPreferredModel(null);
            } catch (Exception e) {
                log.warn("Model [{}] call failed", modelName, e);
                healthCheckService.markUnhealthy(modelName);
                request.setPreferredModel(null);
                lastException = e;
            }
        }

        throw new RuntimeException("All " + maxAttempts + " models exhausted", lastException);
    }

    private String callWithTimeout(ChatModel model, String message) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> model.chat(message), executor)
                    .get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Model call timed out after " + timeoutDuration, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Model call interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Model call failed: " + cause.getMessage(), cause);
        }
    }

    private String findModelName(ChatModel model) {
        return modelMap.entrySet().stream()
                .filter(e -> e.getValue() == model)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model not found in registry"));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public String getLastModelName() {
        return lastModelName.get();
    }
}
