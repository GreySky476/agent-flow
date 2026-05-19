package com.example.agentflow.aspect;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.example.agentflow.entity.GatewayCallLog;
import com.example.agentflow.model.GatewayChatRequest;
import com.example.agentflow.service.GatewayCallLogService;
import com.example.agentflow.service.ResilientChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class GatewayLogAspect {

    private final GatewayCallLogService gatewayCallLogService;
    private final ResilientChatService resilientChatService;

    @Around("execution(* com.example.agentflow.controller.GatewayController.chat(..))")
    public Object logGatewayCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String modelName = null;
        Integer promptTokens = 0;
        Integer completionTokens = 0;
        Boolean success = false;
        String errorMsg = null;
        Instant start = Instant.now();

        GatewayChatRequest request = (GatewayChatRequest) joinPoint.getArgs()[0];
        if (request.getMessage() != null) {
            promptTokens = estimateTokens(request.getMessage());
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            success = true;
            modelName = resilientChatService.getLastModelName();
        } catch (Exception e) {
            errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (errorMsg.length() > 1000) {
                errorMsg = errorMsg.substring(0, 1000);
            }
            throw e;
        } finally {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            if (result instanceof String responseStr && !responseStr.isEmpty()) {
                completionTokens = estimateTokens(responseStr);
            }

            try {
                GatewayCallLog logEntry = new GatewayCallLog();
                logEntry.setRequestId(requestId);
                logEntry.setModelName(modelName);
                logEntry.setPromptTokens(promptTokens);
                logEntry.setCompletionTokens(completionTokens);
                logEntry.setLatencyMs(latencyMs);
                logEntry.setSuccess(success);
                logEntry.setErrorMsg(errorMsg);
                logEntry.setCostEstimate(estimateCost(promptTokens, completionTokens));
                gatewayCallLogService.save(logEntry);
            } catch (Exception e) {
                log.warn("Failed to persist gateway call log", e);
            }
        }

        return result;
    }

    private int estimateTokens(String text) {
        return text.length() / 2;
    }

    private BigDecimal estimateCost(int promptTokens, int completionTokens) {
        BigDecimal inputCost = new BigDecimal("0.000005");
        BigDecimal outputCost = new BigDecimal("0.000015");
        BigDecimal input = BigDecimal.valueOf(promptTokens).divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
        BigDecimal output = BigDecimal.valueOf(completionTokens).divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP);
        return input.multiply(inputCost).add(output.multiply(outputCost)).setScale(6, RoundingMode.HALF_UP);
    }
}
