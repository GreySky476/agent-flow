# 知识库维护规则

> 本文档指导 AI 智能体和开发者如何维护项目知识库。
> 违反以下规则会导致文档与代码失去同步，增加后续维护成本。

---

## 1. 代码变更 → 文档同步

任何代码修改完成后，必须按以下矩阵更新对应文档：

| 变更类型 | 需更新的文档 |
|----------|-------------|
| 新增/修改模块功能 | `docs/modules/{module-name}.md` 对应章节 + 修改历史 |
| 新增/修改 API 端点 | `docs/schema/api-endpoints.md` + 模块文档 |
| 新增/修改数据表或实体 | `docs/schema/schema.md` + 模块文档 |
| 修改模块间依赖关系 | `docs/architecture.md` 模块依赖图 |
| 引入新框架或重大技术决策 | 创建新文件 `docs/adr/{NNNN}-title.md` |
| 修改编码规范 | `docs/conventions.md`（同时检查代码一致性） |
| 仅修改实现逻辑、不改 API | 只需更新模块文档的"修改历史" |

---

## 2. 修改历史格式

所有模块文档、Schema 文档、ADR 文档末尾必须维护"修改历史"表格：

```markdown
## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
| 2026-05-20 | 新增 XXX 功能，详见 `adr/0003.md` | 开发者名 |
```

规则：
- **每次修改都必须新增一行**在最上面（日期倒序）。
- 变更描述应包含"做了什么"和"为什么"（可引用 ADR）。
- 不要删除或修改历史行，只能新增。

---

## 3. ADR（架构决策记录）

- 任何影响系统架构的决策都必须产出 ADR，无论决策大小。
- ADR 格式：`docs/adr/{NNNN}-{url-safe-title}.md`
- 编号规则：按顺序递增（0001, 0002, ...）。
- 标准结构：**标题 → 状态 → 背景 → 决策 → 后果**。
- 状态可选：`已提议` → `已接受` → `已废弃`（勿删除，改为 `已废弃` 并注明替代 ADR）。

触发 ADR 的场景（非穷举）：
- 引入新的第三方库或框架
- 改变模块间依赖关系
- 改变 API 设计范式
- 改变部署或运维方式
- 放弃某个已有的技术决策

---

## 4. 一致性检测

建议定期（每个 Sprint 结束时）运行以下检查：

```bash
# 1. 检查是否有 .java 文件引用了不存在的外部包（编译检测）
./mvnw compile

# 2. 检查 application.yml 中配置的 beans 是否与代码一致
grep -r "ConfigurationProperties" --include="*.java" agent-flow-*/src

# 3. 对比 Schema 文档和 schema.sql
diff <(grep "CREATE TABLE" docs/schema/schema.md | sort) \
     <(grep "CREATE TABLE" agent-flow-app/src/main/resources/resources/db/schema.sql | sort)

# 4. 对比 API 文档和 Controller
diff <(grep "@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)" \
      agent-flow-app/src/main/java/com/example/agentflow/controller/*.java | sort) \
     <(grep "| GET \| POST | PUT | DELETE " docs/schema/api-endpoints.md | sort)
```

---

## 5. 禁止事项

1. **禁止修改代码后跳过文档更新** — 哪怕只是"小改动"。
2. **禁止删除 ADR 文件** — 废弃的 ADR 改为 `已废弃` 状态保留，作为历史参考。
3. **禁止在 AGENTS.md 中写入详细内容** — AGENTS.md 是指针文件，详细内容应放在子文档中。
4. **禁止创建空的或占位性的文档** — 每个文档必须有实际内容，如果模块为空（如 security），应在文档中明确标注"空模块，无实现"，而不是不创建文档。
5. **禁止将过时的 AGENTS.md 作为唯一参考** — 当发现 AGENTS.md 与实际代码不一致时，立即更新。

---

## 6. 知识库文件清单

AI 智能体在操作前应确认以下文件存在且有效：

```
AGENTS.md                          # AI 入口索引
docs/
├── architecture.md                # 高层架构
├── conventions.md                 # 编码规范
├── knowledge-maintenance.md       # 本文件
├── modules/
│   ├── agent-flow-app.md
│   ├── agent-flow-common.md
│   ├── agent-flow-gateway.md
│   ├── agent-flow-rag.md
│   ├── agent-flow-security.md
│   └── agent-flow-workflow.md
├── schema/
│   ├── schema.md                  # 数据库 Schema
│   └── api-endpoints.md           # API 端点
├── adr/
│   ├── 0001-multi-module-maven-structure.md
│   └── 0002-feature-v1-simplification.md
└── roadmap/
    └── current.md                 # 当前需求
tasks/
└── _template.md                   # 任务卡模板
```

---

## 修改历史

| 日期 | 变更 | 作者 |
|------|------|------|
| 2026-05-19 | 初始文档 | AI Agent |
