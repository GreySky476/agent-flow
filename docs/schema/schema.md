# 数据库 Schema

> 数据库：PostgreSQL（pgvector 扩展）  
> DDL 源文件：`agent-flow-app/src/main/resources/resources/db/schema.sql`

---

## 实体关系

```
gateway_call_log   独立表，记录每次 AI 调用的日志

wf_definition ──1:N──→ wf_node
工作流定义              节点（含 type/config/nextNodes）

rag_document        独立表，记录已索引的文档元数据
rag_embeddings      向量表（pgvector），存储文档段落的嵌入向量
```

---

## 表结构

### gateway_call_log（网关调用日志）

```sql
CREATE TABLE gateway_call_log (
    id               BIGSERIAL PRIMARY KEY,
    request_id       VARCHAR(64),
    model_name       VARCHAR(64),
    prompt_tokens    INT,
    completion_tokens INT,
    latency_ms       BIGINT,
    success          BOOLEAN,
    error_msg        TEXT,
    cost_estimate    DECIMAL(10,6),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

| 字段 | 说明 |
|------|------|
| `cost_estimate` | 通过 `AOP GatewayLogAspect` 估算（Prompt 单价 × token 数） |

---

### wf_definition（工作流定义）

```sql
CREATE TABLE wf_definition (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(20) DEFAULT 'DRAFT',
    definition_json  TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

`status` 可选值：`DRAFT` | `PUBLISHED` | `ARCHIVED`

---

### wf_node（工作流节点）

```sql
CREATE TABLE wf_node (
    id               BIGSERIAL PRIMARY KEY,
    definition_id    BIGINT NOT NULL REFERENCES wf_definition(id) ON DELETE CASCADE,
    node_id          VARCHAR(64) NOT NULL,
    node_type        VARCHAR(20) NOT NULL,
    config_json      TEXT,
    position_x       INT DEFAULT 0,
    position_y       INT DEFAULT 0,
    next_nodes       VARCHAR(512)
);
```

| 字段 | 说明 |
|------|------|
| `node_type` | `START` / `END` / `AGENT` / `CONDITION` / `TOOL` |
| `config_json` | JSON 字符串，AGENT 类型存 `{"agentName":"xxx"}`，CONDITION 类型存 `{"expression":"xxx"}` |
| `next_nodes` | 逗号分隔的下游 `node_id` 列表 |
| `position_x/y` | 前端设计器中的坐标 |

---

### rag_document（RAG 文档记录）

```sql
CREATE TABLE rag_document (
    id               BIGSERIAL PRIMARY KEY,
    doc_name         VARCHAR(255),
    file_type        VARCHAR(20),
    chunk_count      INT,
    status           VARCHAR(20) DEFAULT 'INDEXED',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

`status` 可选值：`PENDING` | `INDEXED` | `FAILED`

---

### rag_embeddings（RAG 向量存储）

```sql
CREATE TABLE rag_embeddings (
    embedding_id     VARCHAR(36) PRIMARY KEY,
    embedding        vector(1024),
    text             TEXT,
    metadata         JSONB
);

CREATE INDEX idx_rag_embeddings_text_fts
    ON rag_embeddings USING GIN (to_tsvector('english', text));
```

| 字段 | 说明 |
|------|------|
| `embedding` | pgvector 向量，维度 1024 |
| `metadata` | JSONB，至少包含 `doc_name`, `chunk_index`, `doc_id` |

---

## 注意事项

- `rag_embeddings` 表每次创建时先 `DROP TABLE IF EXISTS`，再建表（`schema.sql` 中如此）
- 全文检索使用 `english` 语言配置，中文支持有限。后续迭代建议切换为 `zhparser` 或 `jieba` 分词
- 当前没有 `memory_entries` / `memory_summaries` / `memory_entities` 表（feature-v1 已移除高级记忆功能）

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
