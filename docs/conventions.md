# 开发规范与约束

> 本文档定义 Agent Flow 项目的编码规范、命名约定、API 设计风格等。
> 标注 `[现状]` 的条目从现有代码中提取；标注 `[建议]` 的条目基于业界最佳实践，尚未在代码中全面落地。

---

## 1. 项目结构约定 `[现状]`

```
模块名/
└── src/main/java/com/example/agentflow/
    ├── annotation/      # 自定义注解
    ├── aspect/           # AOP 切面
    ├── config/           # @Configuration 配置类
    ├── controller/       # @RestController
    ├── entity/           # MyBatis-Plus 实体
    ├── mapper/           # MyBatis-Plus Mapper 接口
    ├── model/            # DTO / 值对象
    ├── service/          # @Service 业务服务
    └── tool/             # @AgentTool 工具 Bean
```

- `agent-flow-app` 是唯一包含 Controller 和入口类的模块。
- 除 `app` 外的模块不含 `controller/`，只向外暴露 Service Bean。

---

## 2. Java 编码规范 `[现状]`

### 2.1 Lombok 使用

- **实体类**：使用 `@Data`
- **DTO**：使用 `@Data` + `@Builder`（如 `ChatRequest`, `QaResponse`）
- **Service**：使用 `@RequiredArgsConstructor` 实现构造器注入，搭配 `final` 字段
- **日志**：使用 `@Slf4j`

```java
// 推荐写法
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartChatRouter {
    private final Map<String, ChatModel> modelMap;
    private final HealthCheckService healthCheckService;
    // ...
}
```

### 2.2 依赖注入

- **始终使用构造器注入**，不使 用 `@Autowired` 字段注入。
- 通过 `@RequiredArgsConstructor` + `final` 字段实现。

### 2.3 配置类

- 标注 `@Configuration`
- 属性映射使用 `@ConfigurationProperties(prefix = "...")` 绑定内部静态类
- 示例：`ModelRegistryConfig`, `RagConfig`, `AgentRegistry`

### 2.4 条件装配

- 可选功能使用 `@ConditionalOnProperty`（如 RAG 通过 `rag.enabled=true` 控制）
- 该注解同时标注在 `@Configuration` 类和 `@Service` / `@RestController` 上

### 2.5 命名约定

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| 实体类 | 名词，对应表名（驼峰） | `WorkflowDefinition` |
| Mapper | `{Entity}Mapper` 实现 `BaseMapper<Entity>` | `WorkflowDefinitionMapper` |
| Service | `{功能}Service` | `ResilientChatService` |
| Controller | `{功能}Controller` | `AdminController` |
| DTO | 名词 + Request/Response 后缀 | `GatewayChatRequest`, `QaResponse` |
| 配置类 | `{功能}Config` | `ModelRegistryConfig` |

### 2.6 代码风格

- **不加注释**（干净代码风格，代码即文档）
- 使用 Java 21 特性：`switch` 表达式、record 类型
- 工具方法使用 `private static` 或 `private` 限定

---

## 3. API 设计规范 `[现状]`

### 3.1 路径约定

```
/api/v1/{module}/{resource}
```

- 所有 API 以 `/api/v1/` 为前缀
- 示例：`/api/v1/gateway/chat`, `/api/v1/agentflow/definitions`

### 3.2 返回格式

- **不强制包装**，根据场景使用：
  - 简单操作返回 `Map.of("success", true, ...)`
  - 查询直接返回实体或 DTO（会自动 Jackson 序列化）
  - 分页返回 `IPage<T>`（MyBatis-Plus 分页对象）

### 3.3 错误处理

- 业务异常直接抛出 `IllegalArgumentException` / `IllegalStateException` / `RuntimeException`
- 目前**没有全局异常处理器**，异常会以 Spring Boot 默认 JSON 格式返回 `[建议]` 建议添加 `@ControllerAdvice` 统一异常处理

### 3.4 HTTP 方法

| 操作 | 方法 | 示例 |
|------|------|------|
| 查询 | GET | `GET /api/v1/admin/models` |
| 创建/提交 | POST | `POST /api/v1/agentflow/definitions` |
| 状态变更 | POST | `POST /api/v1/admin/models/{name}/enable` |

> `[建议]` 状态变更操作应使用 `PUT` 或 `PATCH`，当前全部使用 `POST`。

---

## 4. 数据访问规范 `[现状]`

### 4.1 MyBatis-Plus

- 所有实体标注 `@TableName("表名")` 和 `@TableId(type = IdType.AUTO)`
- Mapper 接口标注 `@Mapper`，继承 `BaseMapper<Entity>`
- 分页需配置 `PaginationInnerInterceptor`（已完成）
- 复杂查询使用 `LambdaQueryWrapper`

### 4.2 事务

- 仅在 `DocumentProcessingService.processDocument()` 上使用了 `@Transactional`
- `[建议]` 涉及多表写入的 Service 方法应统一加上 `@Transactional`

---

## 5. 组件状态覆盖要求 `[建议]`

目前前端部分页面已处理空状态，但未形成统一规范。建议所有数据展示组件覆盖三种状态：

| 状态 | 要求 |
|------|------|
| **Loading** | 数据加载中显示骨架屏或 `v-loading` |
| **Empty** | 无数据时显示 Empty 占位图 + 引导文案 |
| **Error** | 请求失败时显示 Error 状态 + "重试"按钮 |

---

## 6. Git 提交规范 `[建议]`

建议使用 Conventional Commits：

```
<type>(<scope>): <description>

feat(gateway): add fallback to low-cost model on high-tier failure
fix(workflow): handle cyclic graph detection in Kahn algorithm
docs(modules): update change history for gateway module
```

| type | 用途 |
|------|------|
| `feat` | 新增功能 |
| `fix` | 修复 Bug |
| `refactor` | 重构（不改变行为） |
| `docs` | 文档更新 |
| `test` | 测试相关 |
| `chore` | 构建/配置变更 |

---

## 7. 配置文件约定 `[现状]`

- 配置文件格式：**YAML**（`application.yml`），不使用 `.properties`
- 敏感信息使用 `${ENV_VAR:default}` 占位符，真实值在 `.env` 中
- `.env` 文件已在 `.gitignore` 中排除
- `application-local.yml` / `application-dev.yml` 也被排除

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 — 从现有代码提取规范 + 标注建议项 | AI Agent |
