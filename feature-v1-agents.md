# feature-v1 精简计划与变更记录

## 背景

`main` 分支集成了大量高级特性（思考引擎、GraphRAG、MCP、事件总线、WebSocket等），导致系统复杂度高、依赖重。`feature-v1` 精简为核心可交付模块，增强系统稳定性，高级特性作为后续增强考虑。

## 保留模块

### 1. AI Gateway（多模型网关）
- `GatewayController` — POST /api/v1/gateway/chat
- `SmartChatRouter` — cost-tier + health + round-robin 路由
- `ResilientChatService` — 动态熔断 + CompletableFuture 降级
- `ModelRegistryConfig` — openai/ollama/qwen/deepseek 模型注册
- `HealthCheckService` / `ModelHealthChecker` — 指数退避健康检测
- `GatewayLogAspect` — AOP 调用日志

### 2. Basic RAG（基础知识库）
- `RagConfig` — pgvector + OpenAiEmbeddingModel
- `DocumentProcessingService` — ApacheTika 文档解析
- `HybridSearchService` — 向量 + 全文混合检索 (RRF)
- `RagQaService` — RAG 问答（调用 SmartChatRouter）
- `RagController` — 文档上传 + 问答 API

### 3. DAG Engine（工作流引擎）
- `WorkflowDefinition` / `WorkflowNode` — MyBatis-Plus 实体
- `WorkflowExecutor` — Kahn 拓扑排序 + CompletableFuture 并行
- `WorkflowState` — ConcurrentHashMap + Redis 持久化
- `AgentFlowController` — CRUD + Execute + Status + Tools API

### 4. Human Approval（人在回路审批）
- `ApprovalTask` — 审批任务实体
- `ApprovalService` — create/approve/reject
- `ApprovalController` — pending/approve/reject API

### 5. Multi-Tenant（多租户）
- `TenantContext` — ThreadLocal 持有器
- `TenantInterceptor` — MyBatis-Plus 自动添加 tenant_id
- `tenants` / `users` / `roles` 表

### 6. 基础工具框架
- `@AgentTool` 注解 + `ToolRegistry` + `ToolExecutionService`
- `WebSearchTool` — 示例工具（模拟搜索）
- `AgentExecutionService` — AiServices 动态 Agent 调用

## 删除内容

### 文件删除 (26 files)

**Thinking Engine (9):**
AbstractThinkingEngine, ThinkingEngine, ThinkingMode, ReasonerRouter, AgentResponse, ReActEngine, PlanExecuteEngine, ReWOOEngine, ReflectionEngine

**GraphRAG (4):**
GraphExtractionService, GraphStorageService, GraphRagRetriever, RetrievalResult

**MCP (3):**
McpClientService, McpServerExporter, ToolMarketController

**Debug/Health (2):**
DebugPhase2Controller, HealthCheckController

### 依赖删除

| 删除 | 原因 |
|------|------|
| `spring-boot-starter-webflux` | MCP HTTP 客户端 |
| `spring-boot-starter-data-neo4j` | GraphRAG |
| `langchain4j-agentic` | Supervisor/Worker agent |
| `langchain4j-mcp` | MCP 协议 |

### 配置删除

```yaml
neo4j:          # 图数据库
graph:          # 图抽取模板
memory_summaries / memory_entities 表  # 高级记忆
```

### Docker 服务精简

```
Before: PostgreSQL + Redis + Neo4j + RabbitMQ + App
After:  PostgreSQL + Redis + App
```

## 构建验证

```bash
./mvnw clean compile   # ✓ PASS
docker compose up -d    # 仅需 postgres + redis
```

## 后续迭代计划
- V2: 思考引擎 (ReAct / Plan-Execute)
- V3: 事件总线 + WebSocket 实时推送
- V4: GraphRAG 知识图谱
- V5: MCP 工具市场
