# Agent Flow
- 开发中，目前已构建基础功能框架，还在完善中。。。
- AI Agent 工作流编排平台，支持多模型路由、RAG 知识库、图检索、MCP 工具注册与工作流可视化设计。

## 功能

- **智能模型路由** — 多模型动态切换（OpenAI / DeepSeek / Ollama / Azure），自动健康检测与熔断降级
- **RAG 知识库** — 文档上传 → 向量化 → 混合检索（pgvector + PostgreSQL 全文搜索），支持 PDF/Word/PPT/Markdown/图片
- **知识图谱** — LLM 实体抽取 → Neo4j 存储 → 图检索增强问答
- **工作流引擎** — 可视化拖拽设计器（vue-flow），Kahn 拓扑排序，同层节点并行执行
- **思考引擎** — ReAct / Plan-Execute / ReWOO / Reflection 四种思考模式，智能分类自动路由
- **MCP 工具市场** — 本地工具注册、MCP 服务器连接、工具启用/禁用管理
- **对话记忆** — 短期 Redis + 长期 PostgreSQL，自动压缩摘要与实体提取
- **管理后台** — 模型管理、调用日志、工具市场、RAG 测试、记忆监控

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.5, MyBatis-Plus 3.5, LangChain4j 1.13, Resilience4j |
| 数据库 | PostgreSQL + pgvector, Redis, Neo4j |
| 前端 | Vue 3, Element Plus, vue-flow, Vite |
| 部署 | Docker Compose |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/yourname/agent-flow.git
cd agent-flow
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入你的 API Keys
```

### 3. 启动中间件

```bash
docker compose up -d postgres redis neo4j
```

### 4. 初始化数据库

```bash
psql -h localhost -U ai_user -d ai_customer_service -f src/main/resources/db/schema.sql
```

### 5. 启动后端

```bash
./mvnw spring-boot:run
```

### 6. 启动前端

```bash
cd frontend && npm install && npm run dev
```

访问 http://localhost:3000

## 一键部署

```bash
# 确保 Docker 已启动，.env 已配置
./deploy.sh
```

## 项目结构

```
agent-flow/
├── src/main/java/
│   ├── com/agentflow/gateway/          # 思考引擎、记忆管理
│   │   ├── agentflow/core/             # ReAct/Plan/ReWOO/Reflection 引擎
│   │   └── memory/                     # 对话记忆存储
│   └── com/example/agentflow/
│       ├── annotation/                 # @AgentTool 注解
│       ├── aspect/                     # GatewayLogAspect
│       ├── config/                     # ModelRegistry, MyBatisPlus, Rag
│       ├── controller/                 # REST API (9 个 Controller)
│       ├── entity/                     # MyBatis-Plus 实体
│       ├── mapper/                     # MyBatis-Plus Mapper
│       ├── model/                      # DTOs
│       ├── service/                    # 业务服务 (18 个 Service)
│       └── tool/                       # WebSearchTool 示例
├── frontend/                           # Vue 3 + Element Plus
├── src/main/resources/
│   ├── application.yml                 # 主配置
│   └── db/schema.sql                   # 建表 SQL
├── docker-compose.yml                  # 中间件编排
├── Dockerfile                          # 应用镜像
├── deploy.sh                           # 一键部署脚本
└── .env.example                        # 环境变量模板
```

## API 端点

| 模块 | 路径 | 说明 |
|------|------|------|
| Gateway | `POST /api/v1/gateway/chat` | AI 聊天 |
| Admin | `GET /api/v1/admin/models` | 模型管理 |
| Admin | `GET /api/v1/admin/call-logs` | 调用日志 |
| RAG | `POST /api/v1/rag/documents` | 文档上传 |
| RAG | `POST /api/v1/rag/query` | 知识库问答 |
| Workflow | `POST /api/v1/agentflow/execute/{id}` | 执行工作流 |
| Workflow | `GET /api/v1/agentflow/definitions/{id}` | 工作流定义 |
| Tools | `GET /api/v1/tools` | 工具列表 |
| Memory | `GET /api/v1/memory/{sid}/status` | 记忆状态 |
| Health | `GET /api/v1/health/engines` | 引擎状态 |

## License

MIT
