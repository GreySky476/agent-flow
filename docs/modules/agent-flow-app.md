# agent-flow-app — 应用启动与 Web 层

> 包路径：`com.example.agentflow`

---

## 模块职责

作为项目的唯一可执行模块，提供：

- **Spring Boot 入口**：`AgentFlowApplication` 主类
- **Controller 层**：所有 REST API 端点
- **AOP 切面**：调用日志记录
- **全局配置**：MyBatis-Plus 分页插件等

---

## 核心类

### 入口

| 类 | 文件 | 注解 |
|----|------|------|
| `AgentFlowApplication` | `AgentFlowApplication.java` | `@SpringBootApplication`, `@EnableScheduling`, `@MapperScan("com.example.agentflow.mapper")` |

### Controller

| 类 | 文件 | 路径前缀 | 说明 |
|----|------|----------|------|
| `GatewayController` | `controller/GatewayController.java` | `/api/v1/gateway` | AI 聊天入口 |
| `AdminController` | `controller/AdminController.java` | `/api/v1/admin` | 模型管理 + 调用日志 |
| `RagController` | `controller/RagController.java` | `/api/v1/rag` | 文档上传 + RAG 问答 |
| `AgentFlowController` | `controller/AgentFlowController.java` | `/api/v1/agentflow` | 工作流 CRUD + 执行 + 工具列表 |

### AOP

| 类 | 文件 | 职责 |
|----|------|------|
| `GatewayLogAspect` | `aspect/GatewayLogAspect.java` | `@Around` 拦截 `GatewayController.chat()`，记录 token/延迟/成本到 `gateway_call_log` |

### 配置

| 类 | 文件 | 职责 |
|----|------|------|
| `MybatisPlusConfig` | `config/MybatisPlusConfig.java` | 注册 `PaginationInnerInterceptor`（PostgreSQL） |

---

## API 端点总览

### Gateway
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/gateway/chat` | AI 聊天，支持 preferredModel |

### Admin
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/models` | 模型列表（含健康状态） |
| POST | `/api/v1/admin/models/{name}/enable` | 启用模型 |
| POST | `/api/v1/admin/models/{name}/disable` | 禁用模型 |
| GET | `/api/v1/admin/call-logs?page=&size=` | 分页调用日志 |

### RAG
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/rag/documents` | 文档上传（multipart/form-data） |
| POST | `/api/v1/rag/query` | RAG 问答（JSON body: `{question, conversationId}`） |

### Workflow
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/agentflow/definitions` | 保存/更新工作流定义（含节点） |
| GET | `/api/v1/agentflow/definitions/{id}` | 获取定义详情（含节点列表） |
| POST | `/api/v1/agentflow/execute/{definitionId}` | 执行工作流，传入初始参数 |
| GET | `/api/v1/agentflow/status/{instanceId}` | 查询执行状态（Redis） |
| GET | `/api/v1/agentflow/tools` | 已注册工具列表 |

---

## 配置资源

| 文件 | 路径 | 说明 |
|------|------|------|
| `application.yml` | `src/main/resources/application.yml` | Spring Boot + 模型 + RAG + Agent + MCP 配置 |
| `schema.sql` | `src/main/resources/db/schema.sql` | 数据库建表 DDL |

---

## 依赖

| 依赖 | 来源 |
|------|------|
| `agent-flow-common` | 内部模块 |
| `agent-flow-gateway` | 内部模块 |
| `agent-flow-rag` | 内部模块 |
| `agent-flow-workflow` | 内部模块 |
| `agent-flow-security` | 内部模块（空） |
| Spring Boot Web | `spring-boot-starter-web` |
| Spring Boot AOP | `spring-boot-starter-aop` |
| Spring Boot Data Redis | `spring-boot-starter-data-redis` |
| Spring Boot JDBC | `spring-boot-starter-jdbc` |
| Spring Boot Actuator | `spring-boot-starter-actuator` |
| MyBatis-Plus JSqlParser | `mybatis-plus-jsqlparser` |
| PostgreSQL Driver | `org.postgresql:postgresql` |
| Spring Boot Test | `spring-boot-starter-test` (test scope) |

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
