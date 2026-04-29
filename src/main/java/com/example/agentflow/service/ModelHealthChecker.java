package com.example.agentflow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelHealthChecker {

    private static final String PING_MESSAGE = "hi";
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    private final Map<String, ChatModel> modelMap;
    private final HealthCheckService healthCheckService;

    private final Map<String, Instant> nextPingTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 30_000, initialDelay = 10_000)
    public void checkAllModels() {
        if (modelMap.isEmpty()) {
            return;
        }
        Instant now = Instant.now();

        for (var entry : modelMap.entrySet()) {
            String modelName = entry.getKey();
            ChatModel model = entry.getValue();

            Instant nextPing = nextPingTimes.get(modelName);
            if (nextPing != null && now.isBefore(nextPing)) {
                log.debug("Skipping health check for [{}], next ping at {}", modelName, nextPing);
                continue;
            }

            pingModel(modelName, model);
        }
    }

    private void pingModel(String modelName, ChatModel model) {
        try {
            String response = CompletableFuture
                    .supplyAsync(() -> model.chat(PING_MESSAGE))
                    .get(PING_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            if (response != null && !response.isBlank()) {
                onPingSuccess(modelName);
            } else {
                onPingFailure(modelName, "returned empty response");
            }
        } catch (TimeoutException e) {
            onPingFailure(modelName, "timed out after " + PING_TIMEOUT);
        } catch (Exception e) {
            onPingFailure(modelName, e.getMessage());
        }
    }

    private void onPingSuccess(String modelName) {
        consecutiveFailures.remove(modelName);
        nextPingTimes.remove(modelName);
        healthCheckService.markHealthy(modelName);
        log.debug("Model [{}] is healthy", modelName);
    }

    private void onPingFailure(String modelName, String reason) {
        int failures = consecutiveFailures.merge(modelName, 1, Integer::sum);
        Duration backoff = Duration.ofMillis(
                Math.min(BASE_BACKOFF.toMillis() * (1L << Math.min(failures - 1, 6)),
                        MAX_BACKOFF.toMillis()));
        nextPingTimes.put(modelName, Instant.now().plus(backoff));
        healthCheckService.markUnhealthy(modelName);
        log.warn("Model [{}] ping failed (consecutive: {}): {}, next retry in {}",
                modelName, failures, reason, backoff);
    }
}
