# 架构设计

> Agent Flow 是一个 AI Agent 工作流编排平台，采用 **多模块 Maven 分层架构**，每个模块独立构建、按需组合。

---

## 1. 整体架构模式

采用 **模块化单体（Modular Monolith）** 架构：

- 所有模块打包为一个 Spring Boot 应用，部署在单个 JVM 中
- 模块间通过 Spring Bean 注入通信（进程内调用，非 RPC）
- 模块边界清晰，未来可向微服务演进

```
┌─────────────────────────────────────────────────────────────┐
│                     agent-flow-app                            │
│   (Spring Boot 入口、Controller、AOP、全局配置)               │
│         ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│         │ Gateway  │  │   RAG    │  │ Workflow │            │
│         │ (路由/熔断)│  │ (检索/QA)│  │(DAG引擎) │            │
│         └────┬─────┘  └────┬─────┘  └────┬─────┘            │
│              │             │             │                   │
│         ┌────┴─────────────┴─────────────┴────┐              │
│         │          agent-flow-common          │              │
│         │ (实体、Model、Mapper、注解)          │              │
│         └─────────────────────────────────────┘              │
│                                                              │
│    ┌──────────┐                                              │
│    │ Security │  ← 空模块（占位，计划多租户+Auth）             │
│    └──────────┘                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 模块依赖关系

```
agent-flow-app
    ├── agent-flow-common
    ├── agent-flow-gateway
    ├── agent-flow-rag
    ├── agent-flow-workflow
    └── agent-flow-security (空)

agent-flow-gateway → agent-flow-common
agent-flow-rag → agent-flow-common, agent-flow-gateway
agent-flow-workflow → agent-flow-common, agent-flow-gateway
agent-flow-security → agent-flow-common
```

- `agent-flow-common` 是被所有模块共享的基础层，无反向依赖。
- RAG 和 Workflow 依赖 Gateway 是因为它们需要调用 `SmartChatRouter` 获取 AI 模型。

---

## 3. 分层架构（模块内部）

每个模块内部遵循如下分层：

```
controller/  →  service/  →  mapper/
    API 层        业务层       数据层
```

| 层 | 职责 | 技术 |
|----|------|------|
| Controller | 接收 HTTP 请求、参数校验、返回响应 | Spring MVC (`@RestController`) |
| Service | 业务逻辑编排、事务管理 | Spring (`@Service`) |
| Config | Bean 注册、第三方组件配置 | Spring (`@Configuration`) |
| Mapper | 数据库访问 | MyBatis-Plus (`BaseMapper`) |
| Entity | 数据表映射 | MyBatis-Plus (`@TableName`) |
| Model | 传输对象 / DTO | 纯 POJO + Lombok |

---

## 4. 核心数据流

### 4.1 网关聊天流

```
HTTP Request
  → GatewayController.chat()
    → GatewayLogAspect (AOP 环绕，记录日志)
      → ResilientChatService.chat()
        → SmartChatRouter.route()       # 模型选择
          → modelMap.get(name).chat()   # 实际调用
        → CircuitBreaker 包装           # 熔断保护
        → 失败时自动降级到下一健康模型
        → HealthCheckService 更新健康状态
    → GatewayCallLogService.save()      # 持久化日志
  → HTTP Response
```

### 4.2 RAG 问答流

```
HTTP Request
  → RagController.query()
    → RagQaService.answer()
      → HybridSearchService.search()     # 混合检索
        → EmbeddingModel.embed(query)    # 查询向量化
        → PgVectorEmbeddingStore.search  # 向量检索
        → PostgreSQL full-text search    # 全文检索
        → RRF 融合排序                   # 结果合并
      → SmartChatRouter.route(prompt)    # 构造提示词 → 调用 LLM
  → QaResponse (answer + sources)
```

### 4.3 工作流执行流

```
HTTP Request
  → AgentFlowController.execute()
    → WorkflowExecutor.execute()
      → 加载 WorkflowDefinition + WorkflowNode
      → Kahn 拓扑排序 → 分层
      → for each level:
          → parallel CompletableFuture dispatch
            → executeNode(): START | AGENT | CONDITION | END
              → AGENT: AgentExecutionService.execute()
                → SmartChatRouter.route()
                → AiServices.build(tools).chat()
              → CONDITION: SpEL 表达式求值
      → Redis 持久化状态 (wf:state:{instanceId}:{nodeId})
  → HTTP Response
```

---

## 5. 关键技术选型

| 决策 | 选型 | 理由 |
|------|------|------|
| Web 框架 | Spring Boot 3.5 | Java 生态标准，Maven 多模块原生支持 |
| ORM | MyBatis-Plus 3.5 | 轻量级，Lambda 查询，分页插件开箱即用 |
| LLM 抽象 | LangChain4j 1.13 | 统一多模型 API（OpenAI/Azure/Ollama/Qwen） |
| 熔断 | Resilience4j 2.3 | Spring Boot 3 原生支持，动态 CircuitBreaker |
| 向量数据库 | pgvector (PG16) | 与业务库合一，降低运维复杂度 |
| 前端 | Vue 3 + Element Plus | 成熟的企业级 UI 组件库 |
| 流程图 | vue-flow | 支持拖拽式 DAG 设计器 |
| 并发 | Virtual Threads (Java 21) | 避免平台线程池耗尽，适合 I/O 密集型 AI 调用 |
| 部署 | Docker Compose | 轻量级编排，适合单机/开发环境 |

---

## 6. module-gateway 跨模块依赖原则

Workflow 和 RAG 模块需要调用 Gateway 的 `SmartChatRouter` 来获取模型。这遵循以下约束：

- 只注入 `SmartChatRouter`，不直接操作 `modelMap` 或 `HealthCheckService`
- 不自己创建 `ChatModel` 实例，统一通过路由获取
- Gateway 负责所有模型选择、健康检查、熔断逻辑

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
