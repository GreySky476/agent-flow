# API 端点清单

> 所有接口以 `/api/v1/` 为前缀。  
> 响应格式：JSON。未使用统一的响应包装类。

---

## Gateway（AI 网关）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/v1/gateway/chat` | AI 聊天 | `GatewayChatRequest` |

**请求体 `GatewayChatRequest`**：
```json
{
  "message": "你好",
  "conversationId": "optional-session-id",
  "preferredModel": "deepseek-v4-flash"
}
```

**响应**：纯文本字符串（模型回复内容）

---

## Admin（管理后台）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/api/v1/admin/models` | 模型列表（含健康状态） | — |
| POST | `/api/v1/admin/models/{name}/enable` | 启用模型 | Path: `name` |
| POST | `/api/v1/admin/models/{name}/disable` | 禁用模型 | Path: `name` |
| GET | `/api/v1/admin/call-logs` | 分页调用日志 | Query: `page`, `size` |

**模型列表响应**：
```json
[
  {"name": "deepseek-v4-flash", "type": "OPENAI", "healthy": true, "costTier": "LOW"},
  {"name": "tencent/hy3-preview:free", "type": "OPENAI", "healthy": true, "costTier": "MEDIUM"}
]
```

**调用日志响应**：MyBatis-Plus `IPage<GatewayCallLog>`

---

## RAG（知识库）

| 方法 | 路径 | 说明 | 参数/请求体 |
|------|------|------|-------------|
| POST | `/api/v1/rag/documents` | 上传文档 | Form: `file` (MultipartFile) |
| POST | `/api/v1/rag/query` | RAG 问答 | Body: `{"question":"...", "conversationId":"..."}` |

> 整个 RAG Controller 使用 `@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")`，仅在 `rag.enabled=true` 时注册。

**文档上传响应**：
```json
{
  "success": true,
  "docId": 1,
  "docName": "test.pdf",
  "chunkCount": 12
}
```

**RAG 问答响应 `QaResponse`**：
```json
{
  "answer": "根据文档片段...",
  "sources": [
    {"text": "段落内容...", "docName": "test.pdf", "chunkIndex": 0}
  ]
}
```

---

## Workflow（工作流引擎）

| 方法 | 路径 | 说明 | 请求体/参数 |
|------|------|------|-------------|
| GET | `/api/v1/agentflow/definitions` | 工作流列表（分页+搜索） | Query: `page`, `size`, `keyword`, `status` |
| POST | `/api/v1/agentflow/definitions` | 保存工作流定义 | `DefinitionRequest` |
| GET | `/api/v1/agentflow/definitions/{id}` | 获取定义详情 | Path: `id` |
| DELETE | `/api/v1/agentflow/definitions/{id}` | 删除（软归档） | Path: `id` |
| PUT | `/api/v1/agentflow/definitions/{id}/publish` | 发布工作流（升版+可选公开） | Body: `{"isPublic":true}` |
| PUT | `/api/v1/agentflow/definitions/{id}/public` | 切换公开状态 | Body: `{"isPublic":true}` |
| GET | `/api/v1/agentflow/definitions/published` | 已发布且公开的工作流列表 | — |
| POST | `/api/v1/agentflow/execute/{definitionId}` | 执行工作流 | Path: `definitionId`, Body: `Map<String,Object>` |
| POST | `/api/v1/agentflow/chat/{definitionId}` | 工作流聊天（交互式执行，自动持久化到 Redis） | Path: `definitionId`, Body: `{"message":"...","sessionId":"..."}` |
| GET | `/api/v1/agentflow/chat/{definitionId}/history` | 加载聊天历史 | Path: `definitionId`, Query: `sessionId` |
| GET | `/api/v1/agentflow/status/{instanceId}` | 查询执行状态 | Path: `instanceId` |
| GET | `/api/v1/agentflow/tools` | 已注册工具列表 | — |

**列表响应**：
```json
{
  "total": 5,
  "page": 1,
  "size": 10,
  "items": [
    {
      "id": 1,
      "name": "客户服务流程",
      "description": "...",
      "status": "DRAFT",
      "version": 1,
      "isPublic": false,
      "createdAt": "2026-05-19T...",
      "updatedAt": "2026-05-19T..."
    }
  ]
}
```

**保存定义请求体 `DefinitionRequest`**：
```json
{
  "id": null,
  "name": "客户服务流程",
  "description": "自动处理客户咨询",
  "status": "DRAFT",
  "definitionJson": "...",
  "inputSchema": "...",
  "outputSchema": "...",
  "nodes": [
    {
      "nodeId": "start_1",
      "nodeType": "START",
      "configJson": null,
      "positionX": 100,
      "positionY": 100,
      "nextNodes": "llm_1"
    },
    {
      "nodeId": "llm_1",
      "nodeType": "LLM",
      "configJson": "{\"modelName\":\"deepseek-v4-flash\",\"systemPrompt\":\"...\",\"rag\":{\"knowledgeBaseIds\":[1],\"topK\":5},\"toolNames\":[\"web_search\"]}",
      "positionX": 300,
      "positionY": 100,
      "nextNodes": "end_1"
    }
  ]
}
```

**工作流聊天响应**：
```json
{
  "success": true,
  "instanceId": "a1b2c3d4",
  "response": "根据您的问题...",
  "conversationId": "a1b2c3d4"
}
```

**执行响应**：
```json
{
  "success": true,
  "instanceId": "a1b2c3d4",
  "result": { "_instanceId": "a1b2c3d4", "_agent_customer_service_response": "..." }
}
```

**工具列表响应**：
```json
[
  {"name": "web_search", "description": "在互联网上搜索信息", "type": "LOCAL"}
]
```

---

## 错误响应

当前无全局异常处理器。异常由 Spring Boot 默认机制返回：

```json
{
  "timestamp": "2026-05-19T10:00:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/gateway/chat"
}
```

常见业务异常：
- `IllegalArgumentException` → HTTP 400（通过 Spring 默认映射）
- `IllegalStateException` / `RuntimeException` → HTTP 500

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
| 2026-05-19 | Workflow API 扩展：新增列表、删除、发布、公开、已发布列表、聊天端点 | AI Agent |
| 2026-05-19 | 聊天端点新增 sessionId 替换 conversationId，新增 /chat/{id}/history 历史加载；列表默认排除 ARCHIVED | AI Agent |
