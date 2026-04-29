package com.agentflow.gateway.memory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agentflow.model.ChatRequest;
import com.example.agentflow.service.SmartChatRouter;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SmartMemoryService implements MemoryStore {

    private static final String REDIS_SHORT_KEY = "mem:short:%s";
    private static final Duration REDIS_TTL = Duration.ofHours(4);

    private final StringRedisTemplate redis;
    private final MemorySummaryMapper summaryMapper;
    private final MemoryEntityMapper entityMapper;
    private final SmartChatRouter smartChatRouter;

    private final int maxItems;
    private final int triggerTokens;
    private final int keepRecent;

    public SmartMemoryService(StringRedisTemplate redis,
                               MemorySummaryMapper summaryMapper,
                               MemoryEntityMapper entityMapper,
                               SmartChatRouter smartChatRouter,
                               @Value("${memory.short-term.max-items:50}") int maxItems,
                               @Value("${memory.compression.trigger-tokens:2000}") int triggerTokens,
                               @Value("${memory.compression.keep-recent:10}") int keepRecent) {
        this.redis = redis;
        this.summaryMapper = summaryMapper;
        this.entityMapper = entityMapper;
        this.smartChatRouter = smartChatRouter;
        this.maxItems = maxItems;
        this.triggerTokens = triggerTokens;
        this.keepRecent = keepRecent;
    }

    @Override
    public void add(String sessionId, String role, String content) {
        String key = REDIS_SHORT_KEY.formatted(sessionId);
        String entry = System.currentTimeMillis() + "|" + role + "|" + content;
        redis.opsForList().rightPush(key, entry);
        Long size = redis.opsForList().size(key);
        if (size != null && size > maxItems) {
            redis.opsForList().trim(key, -maxItems, -1);
        }
        redis.expire(key, REDIS_TTL);

        // 自动检查是否需要压缩
        if (shouldCompress(sessionId)) {
            CompletableFuture.runAsync(() -> compress(sessionId),
                    Executors.newVirtualThreadPerTaskExecutor());
        }
    }

    @Override
    public List<Message> getRecent(String sessionId, int n) {
        String key = REDIS_SHORT_KEY.formatted(sessionId);
        List<String> entries = redis.opsForList().range(key, -(long) n, -1);
        if (entries == null || entries.isEmpty()) return List.of();
        List<Message> msgs = new ArrayList<>();
        for (String e : entries) {
            String[] parts = e.split("\\|", 3);
            if (parts.length == 3) msgs.add(new Message(parts[1], parts[2], Long.parseLong(parts[0])));
        }
        return msgs;
    }

    @Override
    public void updateSummary(String sessionId, String summary) {
        MemorySummary exist = summaryMapper.selectOne(
                new LambdaQueryWrapper<MemorySummary>().eq(MemorySummary::getSessionId, sessionId));
        if (exist != null) {
            exist.setSummary(summary);
            exist.setUpdatedAt(LocalDateTime.now());
            summaryMapper.updateById(exist);
        } else {
            MemorySummary s = new MemorySummary();
            s.setSessionId(sessionId);
            s.setSummary(summary);
            s.setUpdatedAt(LocalDateTime.now());
            summaryMapper.insert(s);
        }
    }

    @Override
    public String getSummary(String sessionId) {
        MemorySummary s = summaryMapper.selectOne(
                new LambdaQueryWrapper<MemorySummary>()
                        .eq(MemorySummary::getSessionId, sessionId)
                        .orderByDesc(MemorySummary::getUpdatedAt)
                        .last("LIMIT 1"));
        return s != null ? s.getSummary() : null;
    }

    @Override
    public void updateEntityMemory(String sessionId, Map<String, String> entities) {
        for (var e : entities.entrySet()) {
            MemoryEntity exist = entityMapper.selectOne(
                    new LambdaQueryWrapper<MemoryEntity>()
                            .eq(MemoryEntity::getSessionId, sessionId)
                            .eq(MemoryEntity::getEntityName, e.getKey()));
            if (exist != null) {
                exist.setEntityValue(e.getValue());
                exist.setUpdatedAt(LocalDateTime.now());
                entityMapper.updateById(exist);
            } else {
                MemoryEntity me = new MemoryEntity();
                me.setSessionId(sessionId);
                me.setEntityName(e.getKey());
                me.setEntityValue(e.getValue());
                me.setUpdatedAt(LocalDateTime.now());
                entityMapper.insert(me);
            }
        }
    }

    @Override
    public Map<String, String> getEntityMemory(String sessionId) {
        List<MemoryEntity> list = entityMapper.selectList(
                new LambdaQueryWrapper<MemoryEntity>()
                        .eq(MemoryEntity::getSessionId, sessionId));
        Map<String, String> result = new HashMap<>();
        list.forEach(e -> result.put(e.getEntityName(), e.getEntityValue()));
        return result;
    }

    public void compress(String sessionId) {
        log.info("Compressing memory for session [{}]", sessionId);
        List<Message> allMsgs = getRecent(sessionId, Integer.MAX_VALUE);
        if (allMsgs.size() <= keepRecent) return;

        // 需要压缩的消息：前 N - keepRecent 条
        List<Message> toCompress = allMsgs.subList(0, allMsgs.size() - keepRecent);
        String oldSummary = getSummary(sessionId);

        // 调用 LLM 生成新摘要
        String prompt = buildCompressPrompt(toCompress, oldSummary);
        try {
            var model = smartChatRouter.route(ChatRequest.builder().message(prompt).build());
            String newSummary = model.chat(prompt);
            updateSummary(sessionId, newSummary);
            log.info("Summary updated for session [{}], length={}", sessionId, newSummary.length());
        } catch (Exception e) {
            log.warn("Compress summary failed for session [{}]: {}", sessionId, e.getMessage());
        }

        // 抽取实体
        try {
            String entityPrompt = buildEntityExtractPrompt(toCompress);
            var model = smartChatRouter.route(ChatRequest.builder().message(entityPrompt).build());
            String entityResult = model.chat(entityPrompt);
            Map<String, String> entities = parseEntities(entityResult);
            if (!entities.isEmpty()) {
                updateEntityMemory(sessionId, entities);
                log.info("Entities updated for session [{}]: {}", sessionId, entities.keySet());
            }
        } catch (Exception e) {
            log.warn("Entity extraction failed: {}", e.getMessage());
        }

        // 从 Redis 中保留最近 keepRecent 条
        String key = REDIS_SHORT_KEY.formatted(sessionId);
        redis.opsForList().trim(key, -keepRecent, -1);
    }

    public String getContextForLLM(String sessionId) {
        StringBuilder ctx = new StringBuilder();
        String summary = getSummary(sessionId);
        Map<String, String> entities = getEntityMemory(sessionId);
        List<Message> recent = getRecent(sessionId, keepRecent);

        if (summary != null && !summary.isBlank()) {
            ctx.append("## 对话摘要\n").append(summary).append("\n\n");
        }
        if (!entities.isEmpty()) {
            ctx.append("## 已知实体信息\n");
            entities.forEach((k, v) -> ctx.append("- ").append(k).append(": ").append(v).append("\n"));
            ctx.append("\n");
        }
        if (!recent.isEmpty()) {
            ctx.append("## 最近对话\n");
            for (Message msg : recent) {
                ctx.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }
        return ctx.toString();
    }

    private boolean shouldCompress(String sessionId) {
        List<Message> msgs = getRecent(sessionId, maxItems);
        int estimatedTokens = msgs.stream()
                .mapToInt(m -> m.content().length() / 2)
                .sum();
        return estimatedTokens > triggerTokens;
    }

    private String buildCompressPrompt(List<Message> msgs, String oldSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下对话压缩为简洁的摘要（保留关键信息、决策、未解决问题）。");
        sb.append("如果已有旧摘要，请结合更新。输出纯文本摘要，不超过500字。\n\n");
        if (oldSummary != null && !oldSummary.isBlank()) {
            sb.append("旧摘要：\n").append(oldSummary).append("\n\n");
        }
        sb.append("新对话：\n");
        for (Message msg : msgs) {
            sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    private String buildEntityExtractPrompt(List<Message> msgs) {
        StringBuilder sb = new StringBuilder();
        sb.append("从以下对话中提取关键实体，以JSON格式输出：");
        sb.append("{\"entityName\": \"entityValue\", ...}。只返回JSON，不超过10个实体。\n\n");
        for (Message msg : msgs) {
            sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseEntities(String json) {
        try {
            String cleaned = json;
            if (cleaned.contains("```json")) cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            if (cleaned.contains("{")) cleaned = cleaned.substring(cleaned.indexOf("{"));
            if (cleaned.contains("}")) cleaned = cleaned.substring(0, cleaned.lastIndexOf("}") + 1);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(cleaned.trim(), Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse entities: {}", e.getMessage());
            return Map.of();
        }
    }

    @PreDestroy
    void shutdown() {
        // executor is virtual-thread per task, auto-cleaned
    }
}
