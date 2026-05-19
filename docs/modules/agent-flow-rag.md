# agent-flow-rag — 知识库检索增强生成

> 包路径：`com.example.agentflow`

---

## 模块职责

提供基于 pgvector 的 RAG（检索增强生成）能力：

- **文档处理**：多格式文档解析（PDF/Word/PPT/Markdown/文本）
- **向量化存储**：文本分段 → 嵌入向量 → pgvector 存储
- **混合检索**：向量语义检索 + PostgreSQL 全文检索（RRF 融合）
- **RAG 问答**：检索 + 提示词模板 → LLM 生成答案

整个模块由 `rag.enabled: true` 控制开关，关闭后所有 RAG Bean 不加载。

---

## 核心类

### 配置类

| 类 | 文件 | 职责 |
|----|------|------|
| `RagConfig` | `config/RagConfig.java` | 创建 `EmbeddingModel` 和 `PgVectorEmbeddingStore`（需 `rag.enabled=true`） |

### 服务类

| 类 | 文件 | 职责 |
|----|------|------|
| `DocumentProcessingService` | `service/DocumentProcessingService.java` | `processDocument(MultipartFile)` 全流程：解析 → 分段 → 嵌入 → 存储 `[带 @Transactional]` |
| `HybridSearchService` | `service/HybridSearchService.java` | `search(query, maxResults)` 向量检索 + 全文检索 + RRF(k=60) 融合 |
| `RagQaService` | `service/RagQaService.java` | `answer(userQuery, conversationId)` 检索 → 设置提示词 → 调用 SmartChatRouter → 返回 QaResponse |

---

## 文档处理流程

```
上传文件(.pdf/.docx/.pptx/.md/.txt/...)
  → 判断文件类型
    ├─ 文本格式 (.md/.txt/.csv/.json/.xml/.html)：直接读取 UTF-8
    ├─ 二进制格式 (.pdf/.docx/.ppt/.pptx/.png/.jpg/.mp3)：Apache Tika 解析
    └─ 不支持格式 (.zip/.tar/.gz/.7z)：返回警告，不处理
  → DocumentSplitters.recursive(500, 50) 分段
  → EmbeddingModel.embedAll(segments) 向量化
  → PgVectorEmbeddingStore.addAll(embeddings, segments) 存储
  → 写入 rag_document 记录
```

### 分段参数

- 块大小（chunk size）：**500 字符**
- 重叠（overlap）：**50 字符**

---

## 混合检索策略（RRF）

1. 查询向量化 → pgvector 向量检索（top `maxResults*2`）
2. PostgreSQL `to_tsvector` / `plainto_tsquery` 全文检索（top `maxResults*2`）
3. **RRF 融合**：`score = 1/(k + rank)`, k=60
4. 按合并分数降序 → 取 top-N

全文检索索引：
```sql
CREATE INDEX idx_rag_embeddings_text_fts
    ON rag_embeddings USING GIN (to_tsvector('english', text));
```

---

## RAG 问答流程

```
用户问题
  → HybridSearchService.search(query, topN=5)
  → 拼接提示词：
    "你是基于知识库的问答助手。请根据以下文档片段回答...\n
     文档片段：[来源 i: doc_name (chunk n)]\n{text}\n\n
     用户问题：{query}"
  → SmartChatRouter.route(prompt)
  → ChatModel.chat(prompt)
  → QaResponse { answer, sources[] }
```

---

## 核心配置（application.yml）

```yaml
rag:
  enabled: true
  embedding:
    api-key: ${SILICONFLOW_API_KEY:...}
    base-url: https://api.siliconflow.cn/v1
    model-name: BAAI/bge-large-zh-v1.5
    dimension: 1024
  pgvector:
    host: ${DB_HOST:localhost}
    port: ${DB_PORT:5432}
    database: ${DB_NAME:ai_customer_service}
    user: ${DB_USER:ai_user}
    password: ${DB_PASSWORD:ai_password}
```

---

## 依赖

| 依赖 | 来源 |
|------|------|
| `agent-flow-common` | 内部模块 |
| `agent-flow-gateway` | 内部模块（SmartChatRouter） |
| LangChain4j pgvector | `dev.langchain4j:langchain4j-pgvector` |
| LangChain4j PDF parser | `dev.langchain4j:langchain4j-document-parser-apache-pdfbox` |
| LangChain4j POI parser | `dev.langchain4j:langchain4j-document-parser-apache-poi` |
| LangChain4j Tika parser | `dev.langchain4j:langchain4j-document-parser-apache-tika` |

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
