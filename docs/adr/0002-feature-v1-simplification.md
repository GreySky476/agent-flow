# ADR-0002: feature-v1 分支精简移除高级特性

- **状态**：已接受
- **日期**：2026（从 feature-v1-agents.md 回溯）
- **决策者**：开发团队

---

## 背景

`main` 分支集成了大量高级特性（思考引擎 Thinking Engine、GraphRAG 知识图谱、MCP 工具市场客户端/服务端、事件总线、WebSocket），这些特性在项目早期阶段增加了系统复杂度和依赖重量，但尚未在生产环境中得到充分验证。需要精简至核心可交付模块以提升系统稳定性。

---

## 决策

从 `main` 分支分出 `feature-v1` 分支，移除以下组件：

### 代码删除（26 个文件）

| 类别 | 删除内容 |
|------|----------|
| Thinking Engine (9) | `AbstractThinkingEngine`, `ThinkingMode`, `ReasonerRouter`, `AgentResponse`, `ReActEngine`, `PlanExecuteEngine`, `ReWOOEngine`, `ReflectionEngine` |
| GraphRAG (4) | `GraphExtractionService`, `GraphStorageService`, `GraphRagRetriever`, `RetrievalResult` |
| MCP (3) | `McpClientService`, `McpServerExporter`, `ToolMarketController` |
| Debug/Health (2) | `DebugPhase2Controller`, `HealthCheckController` |

### 依赖删除

| 依赖 | 原因 |
|------|------|
| `spring-boot-starter-webflux` | MCP HTTP 客户端 |
| `spring-boot-starter-data-neo4j` | GraphRAG 图存储 |
| `langchain4j-agentic` | Supervisor/Worker 模式 |
| `langchain4j-mcp` | MCP 协议 |

### 配置删除

- `neo4j` 数据库连接配置
- 高级记忆表 `memory_summaries`, `memory_entities`
- Graph 抽取模板配置

### Docker 服务精简

```
移除前: PostgreSQL + Redis + Neo4j + RabbitMQ + App（5 个容器）
移除后: PostgreSQL + Redis + App（3 个容器）
```

---

## 后果

### 正面

1. **系统复杂度降低**：代码文件从 ~64 个减少到 ~38 个，依赖从 ~25 个减少到 ~20 个。
2. **启动更快**：不再初始化 Neo4j 和 MCP 连接，应用启动时间减少。
3. **运维简化**：Docker Compose 只需 2 个中间件容器。
4. **聚焦核心**：团队可以集中精力打磨 Gateway + RAG + Workflow 三大核心能力。

### 负面

1. **功能回退**：失去 Thinking Engine 的 4 种思考模式、GraphRAG 的图增强检索、MCP 的工具市场功能。
2. **API 端点减少**：移除了 ToolMarket、Health/MCP、Debug/Graph/Memory 等端点。
3. **后续需要重新实现**：这些特性计划在 V2~V5 版本中分阶段回归。

### 后续迭代计划

| 版本 | 计划内容 |
|------|----------|
| V2 | 思考引擎（ReAct / Plan-Execute） |
| V3 | 事件总线 + WebSocket 实时推送 |
| V4 | GraphRAG 知识图谱 |
| V5 | MCP 工具市场 |

---

## 参考资料

- 完整变更记录：`feature-v1-agents.md`
- 旧版 AGENTS.md 保留了完整特性描述，已被新 AGENTS.md 替代
