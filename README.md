# Agent Flow

> 本项目的 AI 开发工具为 [opencode](https://opencode.ai)，默认使用模型 `deepseek/deepseek-v4-pro`。

AI Agent 工作流编排平台，支持多模型路由、RAG 知识库检索与可视化 DAG 工作流设计。

## 使用引导

| 你想做什么 | 看这里 |
|-----------|--------|
| 了解项目整体架构与技术栈 | `docs/architecture.md` |
| 查看 API 端点清单 | `docs/schema/api-endpoints.md` |
| 了解数据库表结构 | `docs/schema/schema.md` |
| 查看编码规范 | `docs/conventions.md` |
| 开始新需求开发 | `AGENTS.md` → `docs/roadmap/current.md` |
| AI 智能体入口 | `AGENTS.md` |

### 快速启动

```bash
cp .env.example .env          # 配置 API Keys
docker compose up -d postgres redis   # 启动中间件
./mvnw spring-boot:run        # 启动后端 (8080)
cd frontend && npm install && npm run dev  # 启动前端 (3000)
```

## 技术栈

Java 21 · Spring Boot 3.5 · MyBatis-Plus 3.5 · LangChain4j 1.13 · Resilience4j · PostgreSQL (pgvector) · Redis · Vue 3 + Element Plus

## License

MIT
