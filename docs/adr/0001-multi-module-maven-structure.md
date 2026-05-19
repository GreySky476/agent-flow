# ADR-0001: 采用多模块 Maven 项目结构

- **状态**：已接受
- **日期**：2025（从代码中回溯）
- **决策者**：开发团队

---

## 背景

Agent Flow 需要在一个项目中管理多个独立的能力域：AI 模型网关、RAG 知识库、工作流引擎、安全认证等。需要选择一种项目组织方式，既能保持模块边界的清晰，又能在开发阶段以单体方式快速迭代和部署。

---

## 决策

采用 **Maven 多模块（Multi-Module）项目结构**，将系统拆分为 6 个子模块：

| 模块 | 职责 |
|------|------|
| `agent-flow-common` | 共享实体、DTO、Mapper、注解 |
| `agent-flow-gateway` | AI 模型路由、熔断、健康检查、调用日志 |
| `agent-flow-rag` | 文档处理、向量检索、RAG 问答 |
| `agent-flow-workflow` | DAG 工作流引擎、Agent 框架、工具系统 |
| `agent-flow-security` | 多租户认证鉴权（占位，无实现） |
| `agent-flow-app` | Spring Boot 启动入口、Controller 层 |

所有模块最终打包为一个 Spring Boot JAR，部署在单个 JVM 中运行。

模块间依赖为单向：

```
app → common, gateway, rag, workflow, security
gateway → common
rag → common, gateway
workflow → common, gateway
security → common
```

---

## 后果

### 正面

1. **模块边界清晰**：Gateway / RAG / Workflow 各自封装自己的领域逻辑，不可在模块间随意调用。
2. **可独立测试**：理论上每个模块可独立进行单元测试（当前测试覆盖为零，但结构允许）。
3. **可向微服务演进**：如果未来需要独立扩展某个模块（如 RAG 查询压力大），只需将对应模块提取为新服务。
4. **依赖关系显式化**：`pom.xml` 中的 `<dependency>` 清晰表达了模块间的耦合关系。

### 负面

1. **构建复杂度增加**：每个模块有独立的 `pom.xml`，新增依赖需在父 POM 的 `dependencyManagement` 中声明版本。
2. **common 模块容易膨胀**：如果不加约束，易退化成为"万能共享包"。
3. **增加理解成本**：新开发者需要先理解多层模块依赖关系才能定位代码。

### 风险缓解

- `agent-flow-common` 当前只包含实体、DTO、Mapper 和注解，不包含业务逻辑。
- RAG 和 Workflow 只注入 Gateway 的 `SmartChatRouter`（不直接使用 `modelMap`）。
- `feature-v1` 分支通过删除 / 空置无关模块来保持模块精简。

---

## 备选方案

| 方案 | 优点 | 缺点 |
|------|------|------|
| 单一模块 | 结构简单、构建快 | 边界模糊、耦合严重、难以演进 |
| 独立微服务 | 完全解耦、独立部署伸缩 | 运维成本高、开发调试复杂、当前阶段过度设计 |
| Gradle 多模块 | 构建脚本更简洁 | 团队熟悉 Maven 生态，切换成本高于收益 |

---

## 参考资料

- 父 POM：`/pom.xml`
- 各子模块 POM：`agent-flow-*/pom.xml`
- 依赖关系图：`docs/architecture.md`
