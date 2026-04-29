-- ============================================
-- Agent Flow 数据库初始化脚本
-- 执行方式:
--   psql -h localhost -U ai_user -d ai_customer_service -f schema.sql
-- ============================================

-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 网关调用日志
CREATE TABLE IF NOT EXISTS gateway_call_log (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64),
    model_name VARCHAR(64),
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT,
    success BOOLEAN,
    error_msg TEXT,
    cost_estimate DECIMAL(10,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- RAG 文档记录
CREATE TABLE IF NOT EXISTS rag_document (
    id BIGSERIAL PRIMARY KEY,
    doc_name VARCHAR(255),
    file_type VARCHAR(20),
    chunk_count INT,
    status VARCHAR(20) DEFAULT 'INDEXED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- RAG 向量存储（先删后建，避免维度不匹配）
DROP TABLE IF EXISTS rag_embeddings;
CREATE TABLE rag_embeddings (
    embedding_id VARCHAR(36) PRIMARY KEY,
    embedding vector(1024),
    text TEXT,
    metadata JSONB
);

-- 全文检索 GIN 索引
CREATE INDEX IF NOT EXISTS idx_rag_embeddings_text_fts
    ON rag_embeddings USING GIN (to_tsvector('english', text));

-- 工作流定义
CREATE TABLE IF NOT EXISTS wf_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'DRAFT',
    definition_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 工作流节点
CREATE TABLE IF NOT EXISTS wf_node (
    id BIGSERIAL PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(20) NOT NULL,
    config_json TEXT,
    position_x INT DEFAULT 0,
    position_y INT DEFAULT 0,
    next_nodes VARCHAR(512),
    CONSTRAINT fk_wf_node_definition FOREIGN KEY (definition_id) REFERENCES wf_definition(id) ON DELETE CASCADE
);

-- 记忆存储
CREATE TABLE IF NOT EXISTS memory_entries (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    memory_type VARCHAR(20) DEFAULT 'SHORT_TERM'
);

-- 长期摘要
CREATE TABLE IF NOT EXISTS memory_summaries (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    summary TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 实体记忆
CREATE TABLE IF NOT EXISTS memory_entities (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    entity_value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
