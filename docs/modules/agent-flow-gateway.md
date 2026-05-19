# agent-flow-gateway — AI 网关

> 包路径：`com.example.agentflow`

---

## 模块职责

作为所有 AI 模型调用的**唯一入口点**，提供：

- **多模型注册**：通过 YAML 配置动态注册 OpenAI / Azure / Ollama / Qwen 模型
- **智能路由**：基于成本层级（cost-tier）、健康状态和轮询策略选择最优模型
- **熔断降级**：每个模型独立 CircuitBreaker，失败自动切换到下一健康模型
- **健康检查**：定时 ping 模型，指数退避重试
- **调用日志**：通过 AOP 记录每次调用的 token、延迟、成本估算

---

## 核心类

### 配置类

| 类 | 文件 | 职责 |
|----|------|------|
| `ModelRegistryConfig` | `config/ModelRegistryConfig.java` | 从 `models.list` 读取配置，注册 `ChatModel` Bean 和 `modelMap` |

### 服务类

| 类 | 文件 | 职责 |
|----|------|------|
| `SmartChatRouter` | `service/SmartChatRouter.java` | 路由核心：preferredModel → cost-tier 过滤 → health 过滤 → round-robin |
| `ResilientChatService` | `service/ResilientChatService.java` | 带熔断和超时的模型调用，支持自动降级重试 |
| `HealthCheckService` | `service/HealthCheckService.java` | 内存中维护模型健康状态（ConcurrentHashSet） |
| `ModelHealthChecker` | `service/ModelHealthChecker.java` | `@Scheduled(fixedRate=30s)` 定时 ping + 指数退避 |
| `GatewayCallLogService` | `service/GatewayCallLogService.java` | 继承 `ServiceImpl<GatewayCallLogMapper, GatewayCallLog>` |

### DTO

| 类 | 用途 |
|----|------|
| `ModelInfo` | 模型元数据（name, costTier, ChatModel ref） |

---

## 模型配置格式（application.yml）

```yaml
models:
  list:
    - name: deepseek-v4-flash
      type: openai
      api-key: ${DEEPSEEK_API_KEY:...}
      base-url: https://api.deepseek.com
      model-name: deepseek-v4-flash
      cost-tier: low
```

支持的 `type`：`openai`, `azure`, `ollama`, `qwen`
支持的 `cost-tier`：`low`, `medium`, `high`（默认 medium）

---

## 路由策略

1. 若请求指定了 `preferredModel`，直接使用该模型（需健康）
2. 否则估算消息 token 数 → 映射到 cost-tier
3. 筛选该 cost-tier 中健康的模型
4. 轮询选取一个
5. 若该层级全部不健康，放宽至所有健康模型

---

## 熔断配置

- 故障率阈值：50%
- 滑动窗口：10 次
- 熔断时长：30s
- 超时时长：30s（`resilience4j.timelimiter`）
- 熔断器为**动态创建**（按模型名），非静态配置

---

## 健康检查策略

- 每 30 秒 ping 一次（`"hi"` 消息）
- 单次超时：5 秒
- 首次失败后等待 30s，后续**指数退避**：30s → 60s → 2min → ... → 最多 10min
- 成功后重置

---

## 对外接口

模块对外暴露的核心 Bean：

| Bean | 类型 | 说明 |
|------|------|------|
| `SmartChatRouter` | Service | 被 RAG 和 Workflow 模块注入以获取模型 |
| `HealthCheckService` | Service | 被 AdminController 注入以管理模型启停 |
| `modelMap` | `Map<String, ChatModel>` | 模型名 → ChatModel 实例 |

---

## 依赖

| 依赖 | 来源 |
|------|------|
| `agent-flow-common` | 内部模块 |
| Resilience4j Spring Boot 3 Starter | `io.github.resilience4j` |
| LangChain4j OpenAI / Azure / Ollama | `dev.langchain4j` |

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
