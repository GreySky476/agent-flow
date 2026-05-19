# AGENTS.md — Agent Flow 知识库索引

> AI 智能体入口：本项目所有文档的导航地图。不要在本文档中查找细节，这里只告诉你**去哪里找**。

---

## 🧭 快速定位

| 我要找… | 去这里 |
|----------|--------|
| 项目整体架构 | `docs/architecture.md` |
| 编码规范与约定 | `docs/conventions.md` |
| 模块详情 (gateway/rag/workflow) | `docs/modules/{module-name}.md` |
| 数据库 Schema | `docs/schema/schema.md` |
| API 端点清单 | `docs/schema/api-endpoints.md` |
| 架构决策记录 | `docs/adr/` |
| 当前需求 / Epic | `docs/roadmap/current.md` |
| 任务卡模板 | `tasks/_template.md` |
| 知识库维护规则 | `docs/knowledge-maintenance.md` |
| feature-v1 精简变更记录 | `feature-v1-agents.md` |

---

## 🤖 可用工具与命令

```bash
# ── 构建 ──
./mvnw verify                  # 编译 + 测试（全模块）
./mvnw verify -DskipTests      # 仅编译，跳过测试
./mvnw compile                 # 仅编译主源码

# ── 测试 ──
./mvnw test                    # 运行全部测试
./mvnw test -pl agent-flow-app -Dtest=ClassName   # 单测

# ── 运行 ──
./mvnw spring-boot:run         # 启动后端 (8080)
cd frontend && npm install && npm run dev   # 启动前端 (3000)

# ── 代码检查 ──
./mvnw checkstyle:check        # 如有配置

# ── 环境 ──
docker compose up -d postgres redis   # 启动中间件
docker compose down                   # 停止
./deploy.sh                           # 一键部署
```

---

## ⚠️ 铁律

AI 智能体必须绝对遵守以下规则，违反即为失败：

1. **先读 `AGENTS.md` 定位**，再读目标文档，再动手改代码。
2. **不可删除或跳过测试**。生成新代码后必须执行 `./mvnw test` 验证通过。
3. **修改代码后必须更新对应模块文档** 的"修改历史"表格（见 `docs/knowledge-maintenance.md`）。
4. **新增架构决策必须产出 ADR** 到 `docs/adr/`。
5. **不可直接操作 `main` 或 `master` 分支**。所有开发在 feature 分支上完成。
6. **不可修改以下文件**：`pom.xml`（除非新增依赖）、`application.yml`（除非新增配置项且经过确认）、`.env` / `.env.example`（密钥文件）。
7. **遇到编译错误不要猜测**。阅读报错信息和相关源码后再修复。
8. **新功能必须有测试覆盖**。`agent-flow-app/src/test/` 中的测试目录当前为空，需要时按 `tasks/_template.md` 补齐。

---

## 🔄 标准开发流程

```
领取任务 → 阅读相关模块文档 → 编写代码 → ./mvnw test 自检 → 更新模块文档"修改历史" → 新决策写 ADR → 提交 PR
```


---

## 📦 模块速查

| 模块 | 包路径 | 一句话职责 |
|------|--------|-----------|
| `agent-flow-common` | `com.example.agentflow` | 共享实体、模型、注解、Mapper |
| `agent-flow-gateway` | `com.example.agentflow` | AI 模型路由、熔断、健康检查 |
| `agent-flow-rag` | `com.example.agentflow` | 文档解析、向量检索、RAG 问答 |
| `agent-flow-workflow` | `com.example.agentflow` | DAG 工作流引擎、Agent 框架、工具系统 |
| `agent-flow-security` | — | **空模块**（仅占位，无实现代码） |
| `agent-flow-app` | `com.example.agentflow` | 启动入口、Controller、AOP、配置 |

> 详细文档见 `docs/modules/` 下各模块 `.md`。

---

## 🌿 当前分支

`feature-v1` — 精简后的核心可交付版本。与 `main` 相比已移除：思考引擎、GraphRAG、MCP 客户端/服务端、事件总线、WebSocket。详见 `feature-v1-agents.md`。
