# agent-flow-workflow — 工作流引擎与 Agent 框架

> 包路径：`com.example.agentflow`

---

## 模块职责

提供工作流编排和 LLM 节点执行能力：

- **DAG 工作流引擎**：Kahn 拓扑排序实现分层并行执行
- **LLM 节点**：内联配置模型、System Prompt、RAG 增强、工具增强
- **条件分支**：8 种条件类型（contains/notContains/regex/eq/neq/gt/lt/gte/lte）+ SpEL 表达式
- **工具系统**：`@AgentTool` 注解扫描 → 注册 → 反射执行
- **版本管理**：发布时锁定创建新版本，支持公开/不公开
- **状态持久化**：Redis 存储工作流实例状态

---

## 核心类

### 服务类

| 类 | 文件 | 职责 |
|----|------|------|
| `WorkflowExecutor` | `service/WorkflowExecutor.java` | DAG 工作流执行引擎 |
| `LlmNodeExecutor` | `service/LlmNodeExecutor.java` | LLM 节点执行（SmartChatRouter + RAG/Tool 增强） |
| `BranchEvaluator` | `service/BranchEvaluator.java` | 条件分支求值器（8 种条件类型 + SpEL） |
| `WorkflowVersionService` | `service/WorkflowVersionService.java` | 工作流版本管理（发布/公开/查询） |
| `ToolRegistry` | `service/ToolRegistry.java` | 工具注册中心（@EventListener(ApplicationReadyEvent) 扫描） |
| `ToolExecutionService` | `service/ToolExecutionService.java` | 工具反射调用（LOCAL/MCP 分发） |
| `RagToolProvider` | `service/RagToolProvider.java` | RAG 检索工具（LLM 可通过 Tool 调用知识库搜索） |
| `SpringContextHolder` | `service/SpringContextHolder.java` | 静态获取 Spring Bean（用于 RAG 工具注入） |

### 工具实现

| 类 | 文件 | 说明 |
|----|------|------|
| `WebSearchTool` | `tool/WebSearchTool.java` | 示例工具：`web_search` + `fetch_page`（模拟实现） |

---

## 工作流执行流程

```
POST /api/v1/agentflow/execute/{definitionId}
  → WorkflowExecutor.execute()
    1. 加载 WorkflowDefinition + WorkflowNode（从 DB）
    2. 构建邻接表 + 入度图
    3. Kahn 拓扑排序 → 分层列表
    4. 逐层执行：
       同层节点 parallel(CompletableFuture + VirtualThreads)
       executeNode():
         START  → 标记 _started
         LLM   → LlmNodeExecutor.execute(node, state)
         BRANCH → BranchEvaluator.evaluate(node, state)
         END   → 标记 _completed
       每层超时 = NODE_TIMEOUT × 当前层节点数
    5. 每节点完成后 persistState() 到 Redis (TTL 1h)
    6. 返回 state.getData()
```

### 节点类型

| 类型 | 说明 | 配置项 |
|------|------|--------|
| `START` | 起始节点 | 无 |
| `END` | 结束节点 | 无 |
| `LLM` | LLM 调用（内联配置 + 可选 RAG/Tool 增强） | `modelName`, `systemPrompt`, `temperature`, `rag`, `toolNames` |
| `BRANCH` | 条件分支 | `conditionType`, `expression`/`leftField`+`rightValue` |

---

## LLM 节点执行流程

```
LlmNodeExecutor.execute(node, state)
  1. 解析 configJson → modelName, systemPrompt, rag, toolNames
  2. SmartChatRouter.route(modelName) → ChatModel
  3. 若 rag 配置不为空 → 构造 RagToolProvider
  4. 若 toolNames 不为空 → ToolRegistry.getTools()
  5. AiServices.builder(LlmChatService.class)
       .chatModel(chatModel)
       .systemMessage(systemPrompt)
       .tools(ragTool, ...otherTools)
       .build()
  6. aiService.chat(userMessage) → AI 自主决定调用 RAG/Tool
  7. 响应写入 state
```

### LLM 节点 JSON 配置示例

```json
{
  "modelName": "deepseek-v4-flash",
  "systemPrompt": "你是一个客户服务助手",
  "temperature": 0.7,
  "rag": {
    "knowledgeBaseIds": [1, 2],
    "topK": 5
  },
  "toolNames": ["web_search"]
}
```

---

## 分支节点

支持 9 种条件类型：

| 条件类型 | 说明 | 配置字段 |
|----------|------|----------|
| `CONTAINS` | 包含 | `leftField`, `rightValue` |
| `NOT_CONTAINS` | 不包含 | `leftField`, `rightValue` |
| `REGEX` | 正则匹配 | `leftField`, `rightValue` |
| `EQ` | 等于 | `leftField`, `rightValue` |
| `NEQ` | 不等于 | `leftField`, `rightValue` |
| `GT` | 大于 | `leftField`, `rightValue` |
| `LT` | 小于 | `leftField`, `rightValue` |
| `GTE` | 大于等于 | `leftField`, `rightValue` |
| `LTE` | 小于等于 | `leftField`, `rightValue` |
| `EXPRESSION` | SpEL 表达式 | `expression` |

---

## 版本管理

- **保存 DRAFT**：直接更新当前定义，不升版
- **发布**：创建新的 PUBLISHED 版本（复制节点），version 自增
- **公开/不公开**：已发布版本可切换 `isPublic`，公开后在工作流聊天可见
- **软删除**：设置 status=ARCHIVED

---

## 工具系统

### 注册方式

`ToolRegistry` 监听 `ApplicationReadyEvent`，扫描所有 `@Component` / `@Service` Bean 上标注 `@AgentTool` 的方法，自动注册。

### @AgentTool 注解

```java
@AgentTool(name = "web_search", description = "搜索互联网", exportable = true)
public String search(String query) { ... }
```

| 属性 | 说明 |
|------|------|
| `name` | 工具名称（框架调用时使用） |
| `description` | 工具描述（LLM 用来判断何时调用） |
| `exportable` | 是否可通过 MCP 导出（feature-v1 中 MCP 已移除，此字段保留） |

### 执行方式

`ToolExecutionService.execute(toolName, params)` → 按 `ToolType` 分发：
- `LOCAL`：反射调用 `method.invoke(bean, args)`
- `MCP`：返回错误（feature-v1 不支持）

---

## 依赖

| 依赖 | 来源 |
|------|------|
| `agent-flow-common` | 内部模块 |
| `agent-flow-gateway` | 内部模块（SmartChatRouter） |
| `agent-flow-rag` | 内部模块（HybridSearchService，RAG 工具） |
| Spring Boot Data Redis | `org.springframework.boot:spring-boot-starter-data-redis` |

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
| 2026-05-19 | 移除 AGENT 节点类型，新增 LLM/BRANCH 节点类型 | AI Agent |
| 2026-05-19 | 新增 BranchEvaluator（8 种条件类型）、LlmNodeExecutor（RAG/Tool 增强）、WorkflowVersionService | AI Agent |
| 2026-05-19 | 新增 RAG 依赖，RagToolProvider 通过 SpringContextHolder 获取 HybridSearchService | AI Agent |
| 2026-05-19 | LlmNodeExecutor 新增 GatewayCallLog 记录；发布改为同行更新（不再创建版本快照）；默认排除 ARCHIVED；聊天 Redis 会话持久化 | AI Agent |
