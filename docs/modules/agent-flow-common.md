# agent-flow-common — 共享层

> 包路径：`com.example.agentflow`

---

## 模块职责

为其他所有模块提供共享的基础设施，包括：

- **实体类（Entity）**：MyBatis-Plus 数据表映射
- **传输对象（Model / DTO）**：跨层/跨模块传输的数据对象
- **Mapper 接口**：数据库访问接口
- **自定义注解**：如 `@AgentTool`

此模块**不包含**任何业务逻辑或 Service。

---

## 导出内容

### 实体类

| 类 | 对应表 | 说明 |
|----|--------|------|
| `WorkflowDefinition` | `wf_definition` | 工作流定义 |
| `WorkflowNode` | `wf_node` | 工作流节点（含节点类型、坐标、下游节点） |
| `GatewayCallLog` | `gateway_call_log` | 网关调用日志 |
| `RagDocument` | `rag_document` | RAG 文档记录 |

### DTO / Model

| 类 | 用途 |
|----|------|
| `ChatRequest` | 内部模型路由请求（preferredModel + message） |
| `GatewayChatRequest` | 网关聊天请求（message + conversationId + preferredModel） |
| `QaResponse` | RAG 问答响应（answer + sources） |
| `WorkflowState` | 工作流状态容器（ConcurrentHashMap + JSON 序列化） |
| `AgentConfig` | Agent 配置（name/sysPrompt/toolNames/modelPreference） |
| `ModelInfo` | 模型路由信息（name/costTier/ChatModel） |

### Mapper 接口

| 接口 | 实体 | 基类 |
|------|------|------|
| `WorkflowDefinitionMapper` | `WorkflowDefinition` | `BaseMapper<T>` |
| `WorkflowNodeMapper` | `WorkflowNode` | `BaseMapper<T>` |
| `GatewayCallLogMapper` | `GatewayCallLog` | `BaseMapper<T>` |
| `RagDocumentMapper` | `RagDocument` | `BaseMapper<T>` |

### 注解

| 注解 | 用途 |
|------|------|
| `@AgentTool` | 标记工具方法：`name`, `description`, `exportable` |

---

## 依赖

| 依赖 | 来源 |
|------|------|
| MyBatis-Plus | `com.baomidou:mybatis-plus-spring-boot3-starter` |
| LangChain4j | `dev.langchain4j:langchain4j` |
| Jackson YAML | `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` |

---

## 关键约束

- 实体类使用 `@Data` + `@TableName` + `@TableId(type = IdType.AUTO)`
- Mapper 必须标注 `@Mapper`，继承 `BaseMapper<Entity>`
- DTO 尽量使用 `@Builder` + `@Data`
- 不在此模块中写任何 `@Service` 业务逻辑

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
