# agent-flow-workflow — 工作流引擎与 Agent 框架

> 包路径：`com.example.agentflow`

---

## 模块职责

提供工作流编排和 Agent 执行能力：

- **DAG 工作流引擎**：Kahn 拓扑排序实现分层并行执行
- **Agent 框架**：动态 Agent 定义 + AiServices 绑定工具调用
- **工具系统**：`@AgentTool` 注解扫描 → 注册 → 反射执行
- **状态持久化**：Redis 存储工作流实例状态

---

## 核心类

### 配置

| 类 | 文件 | 职责 |
|----|------|------|
| `AgentRegistry` | `service/AgentRegistry.java` | 从 `agentflow.agents.list` 加载 Agent 配置 |

### 服务类

| 类 | 文件 | 职责 |
|----|------|------|
| `WorkflowExecutor` | `service/WorkflowExecutor.java` | DAG 工作流执行引擎 |
| `AgentExecutionService` | `service/AgentExecutionService.java` | 单个 Agent 节点执行（AiServices 绑定工具） |
| `ToolRegistry` | `service/ToolRegistry.java` | 工具注册中心（@EventListener(ApplicationReadyEvent) 扫描） |
| `ToolExecutionService` | `service/ToolExecutionService.java` | 工具反射调用（LOCAL/MCP 分发） |

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
         START    → 标记 _started
         AGENT    → AgentExecutionService.execute(agentName, state)
         CONDITION → SpEL evaluate(expression)
         END      → 标记 _completed
       每层超时 = NODE_TIMEOUT × 当前层节点数
    5. 每节点完成后 persistState() 到 Redis (TTL 1h)
    6. 返回 state.getVariables()
```

### 节点类型

| 类型 | 说明 | 配置项 |
|------|------|--------|
| `START` | 起始节点 | 无 |
| `END` | 结束节点 | 无 |
| `AGENT` | Agent 调用 | `configJson.agentName` |
| `CONDITION` | 条件分支 | `configJson.expression`（SpEL） |

---

## Agent 执行流程

```
AgentExecutionService.execute(agentName, state)
  1. AgentRegistry.getAgentConfig(agentName)
  2. SmartChatRouter.route(preferredModel) → ChatModel
  3. ToolRegistry.getTools(toolNames[]) → Object[] toolInstances
  4. AiServices.builder(AgentChatService.class)
       .chatModel(chatModel)
       .systemMessage(systemPrompt + context)
       .tools(toolObjects)
       .build()
  5. aiService.chat(userMessage)
  6. 结果写入 state
```

### Agent 配置（application.yml）

```yaml
agentflow:
  agents:
    list:
      - name: customer_service
        system-prompt: 你是一个客户服务助手...
        tool-names:
          - search_knowledge_base
          - create_ticket
        model-preference: deepseek-v4-flash
```

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

## 配置

```yaml
agentflow:
  workflow-definition-storage: database
  max-concurrent-agents: 10
  default-timeout-seconds: 120
```

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `workflow-definition-storage` | 工作流定义存储方式 | `database` |
| `max-concurrent-agents` | 最大并发 Agent 数 | `10` |
| `default-timeout-seconds` | 默认超时秒数 | `120` |

---

## 依赖

| 依赖 | 来源 |
|------|------|
| `agent-flow-common` | 内部模块 |
| `agent-flow-gateway` | 内部模块（SmartChatRouter） |
| Spring Boot Data Redis | `org.springframework.boot:spring-boot-starter-data-redis` |

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
