# AGENTS.md

## Build & Run

```bash
# Compile and run tests (single-module Maven project)
./mvnw verify

# Skip tests for faster compile
./mvnw verify -DskipTests

# Run the Spring Boot application
./mvnw spring-boot:run
```

## Test

```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=AgentFlowApplicationTests
```

## Tech Stack

- **Java 21**, **Spring Boot 3.5.15-SNAPSHOT**, **Maven** (wrapper provided as `./mvnw`)
- Dependencies: Spring Web, Spring JDBC, Spring Data Redis, Spring Actuator, Spring AOP, MyBatis-Plus 3.5.12, Lombok, LangChain4j 1.13.1(+beta23), Resilience4j 2.3.0, PostgreSQL driver
- Additional: Spring WebFlux, Spring Data Neo4j, LangChain4j MCP/Agentic/PgVector/Tika, Jackson YAML
- Single module, package `com.example.agentflow`, entrypoint `AgentFlowApplication.java`
- Config format: `application.yml` (YAML), not `.properties`

## Environment

- Database: PostgreSQL (`localhost:5432/ai_customer_service`, configured in `application.yml`)
- Redis: auto-configured (default `localhost:6379`, no password — managed as `StringRedisTemplate`)
- Requires Spring Snapshot repository (`https://repo.spring.io/snapshot`) for the Boot parent POM

## Model Configuration

- `ModelRegistryConfig` in `config/` reads a model list from `application.yml` under `models.list`
- Each model config: `name`, `type` (openai/azure/ollama/qwen), `api-key`, `base-url`, `model-name`, `cost-tier` (low/medium/high, defaults to medium)
- `ChatModel` beans are auto-registered with the model `name` as the bean name, injectable via `@Qualifier("name")` (注: LangChain4j 1.13.1 中 `ChatLanguageModel` 已重命名为 `ChatModel`)
- A `Map<String, ChatModel>` bean named `modelMap` collects all models

## Routing

- `SmartChatRouter` in `service/`: selects a model by `preferredModel` → cost-tier estimation → health filtering → round-robin
- `HealthCheckService` in `service/`: in-memory unhealthy model tracking, other components can call `markUnhealthy`/`markHealthy`
- `ChatRequest` in `model/`: carries `preferredModel` (optional) and `message`
- `ResilientChatService` in `service/`: wraps model calls with dynamic `CircuitBreaker` (per model name) + `CompletableFuture` timeout; falls back to next healthy model on failure or open circuit
- `ModelHealthChecker` in `service/`: `@Scheduled(fixedRate=30s)` sends a "hi" ping to each model with 5s timeout; marks healthy/unhealthy via `HealthCheckService`; exponential backoff on failure (30s → 60s → 2min → … → 10min max)
- `@EnableScheduling` is on `AgentFlowApplication`

## Resilience4j

- Spring Boot 3 starter (`resilience4j-spring-boot3`) with annotation support; however model circuit breakers are created programmatically via `CircuitBreakerRegistry` since model names are dynamic
- Default circuit breaker config: 50% failure rate, sliding window 10, open-state 30s
- Timeout per call: `resilience4j.timelimiter.configs.default.timeout-duration` (defaults to 30s)

## Gateway

- `GatewayController` in `controller/`: `POST /api/v1/gateway/chat` accepts `GatewayChatRequest` (message, optional conversationId/preferredModel), returns model response
- `GatewayLogAspect` in `aspect/`: AOP around-controller records requestId, model name, token estimates, latency, success/error to `gateway_call_log` table via MyBatis-Plus
- `GatewayCallLog` entity → `gateway_call_log` table; DDL at `src/main/resources/db/schema.sql`
- Token estimation: `text.length() / 2` for both prompt and completion; cost estimate uses flat rates
- `@MapperScan({"com.example.agentflow.mapper", "com.agentflow.gateway.memory"})` is on `AgentFlowApplication`
- `@ComponentScan(basePackages = {"com.example.agentflow", "com.agentflow"})` is on `AgentFlowApplication` (for ThinkingEngine and Memory packages)

## Admin

- `AdminController` in `controller/`: `GET /api/v1/admin/models` (model list with health), `POST /api/v1/admin/models/{name}/enable|disable`, `GET /api/v1/admin/call-logs?page=&size=` (paginated logs)
- `MybatisPlusConfig` in `config/`: configures `PaginationInnerInterceptor` for MyBatis-Plus page queries (requires `mybatis-plus-jsqlparser` dep)
- Frontend at `frontend/`: Vue 3 + Element Plus + Vite, `npm install && npm run dev` on port 3000, proxies `/api` to `localhost:8080`

## RAG

- **Gate**: all RAG beans use `@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")` — disabled by default
- `RagConfig` in `config/`: reads `rag.pgvector.*` and `rag.embedding.*` from YAML; creates `OpenAiEmbeddingModel` (API-based, no ONNX native libs), `PgvectorEmbeddingStore` (table `rag_embeddings`, vector dimension from config)
- `DocumentProcessingService` in `service/`: `processDocument(MultipartFile)` → parse (PDF/DOCX/PPT/PPTX/MD via `ApacheTikaDocumentParser`, text formats directly) → split (`DocumentSplitters.recursive(500, 50)`) → embed → store to pgvector; saves record to `rag_document` table
- `HybridSearchService` in `service/`: `search(query, maxResults)` → vector search (`EmbeddingSearchRequest`) + PostgreSQL full-text search (`to_tsvector`/`plainto_tsquery` on `rag_embeddings`) → RRF fusion (k=60) → top-N results
- `RagQaService` in `service/`: `answer(userQuery, conversationId)` → hybrid search (top 5) → build RAG prompt → route via `SmartChatRouter` → return `QaResponse {answer, sources[]}`
- `RagController` in `controller/`: `POST /api/v1/rag/documents` (upload), `POST /api/v1/rag/query` (Q&A)
- Entities: `RagDocument` → `rag_document` table; `QaResponse` → DTO with `answer` + `sources`
- DDL: `schema.sql` includes `rag_document`, `rag_embeddings` (pgvector), GIN full-text index
- Frontend: `RAGTest.vue` page at `/rag` — upload panel + Q&A panel with source highlights

## Agent Framework

- **Agent config** in `application.yml` under `agentflow.agents.list`: `AgentRegistry` loads `AgentConfig {name, systemPrompt, toolNames[], modelPreference}` at startup
- `AgentExecutionService` in `service/`: `execute(agentName, state)` → gets agent config → picks model via `SmartChatRouter` → builds `AiServices` (system prompt + tools bound) → calls `chat()` → returns response in `WorkflowState`
- **Tools**: `@AgentTool(name, description)` custom annotation marks tool methods on Spring Beans
- `ToolRegistry` in `service/`: `@EventListener(ApplicationReadyEvent)` scans all `@Component`/`@Service` beans for `@AgentTool` methods, registers into `Map<String, ToolEntry>`; also supports MCP tools via `registerMcpClient()`
- `ToolExecutionService` in `service/`: `execute(toolName, params)` → LOCAL (reflect invoke) or MCP (client.executeTool)
- `WebSearchTool` in `tool/`: sample @AgentTool bean with `web_search` and `fetch_page` methods (mock impl)
- `@AgentTool` annotation in `annotation/AgentTool.java`

## Workflow Engine

- **Entities**: `WorkflowDefinition` → `wf_definition`; `WorkflowNode` → `wf_node` (DDL in `schema.sql`)
- `WorkflowExecutor` in `service/`: `execute(definitionId, initialInput)` → loads definition + nodes → Kahn topological sort → executes level-by-level (same-level nodes run in parallel via `CompletableFuture`) → dispatches by `nodeType`:
  - `START` → pass through; `END` → collect output
  - `AGENT` → `AgentExecutionService.execute()`; `TOOL` → `ToolExecutionService.execute()`
  - `CONDITION` → SpEL expression evaluation
- `WorkflowState` in `model/`: `ConcurrentHashMap`-based state container, UUID `workflowInstanceId`, JSON serialization (`toJson`/`fromJson`), Redis persistence per node
- `AgentFlowController` in `controller/`:
  - `POST /api/v1/agentflow/definitions` — save workflow (definition + nodes)
  - `GET /api/v1/agentflow/definitions/{id}` — get definition detail
  - `POST /api/v1/agentflow/execute/{definitionId}` — execute workflow
  - `GET /api/v1/agentflow/status/{instanceId}` — query execution status from Redis
  - `GET /api/v1/agentflow/tools` — list registered tools
- Frontend: `AgentDesigner.vue` at `/agent-designer` — vue-flow drag-and-drop designer; double-click Agent/Tool/Condition nodes for config dialogs; save + execute buttons

## Thinking Engine

- **Package**: `com.agentflow.gateway.agentflow.core` (needs `@ComponentScan("com.agentflow")` on `AgentFlowApplication`)
- `ThinkingMode` enum: `REACT`, `PLAN_EXECUTE`, `REWOO`, `REFLECTION`
- `AbstractThinkingEngine`: template base with logging, step counter, exception handling → `doExecute()` abstract method
- 4 engine implementations (all `@Component`, all inject `SmartChatRouter` + `ToolExecutionService`):

| Engine | Mode | Strategy |
|--------|------|----------|
| `ReActEngine` | REACT | Thought→Action→Observation loop, max 10 rounds |
| `PlanExecuteEngine` | PLAN_EXECUTE | LLM generates JSON plan → step-by-step execution, 120s total timeout |
| `ReWOOEngine` | REWOO | One-shot tool planning → parallel `CompletableFuture` execution → summarize |
| `ReflectionEngine` | REFLECTION | Generate → reflect (quality check) → refine, max 2 reflections |

- `ReasoningRouter` in `core/`: `route(state)` → `classify(query)` via low-cost LLM → `executeWithFallback(mode, state, config)` → fallback to REACT on failure
- `AgentResponse` DTO: `output`, `toolCalls[]`, `intermediateSteps[]`, `completed`, `error`

## Graph RAG

- `GraphExtractionService` in `service/`: `extractEntitiesAndRelations(text)` → splits into 2000-token chunks → LLM extracts JSON `{entities, relations}` → deduplicates by id
- `GraphStorageService` in `service/`: uses `neo4j.Driver` (auto-configured by `spring-boot-starter-data-neo4j`) → `mergeGraph()` batch MERGE, `queryGraph()` 2-hop subgraph, `searchRelevantEntities()` keyword match
- `GraphRagRetriever` in `service/`: combines `HybridSearchService` + `GraphStorageService` → returns `List<RetrievalResult>` (type: vector/graph/graph-subgraph)
- `RagQaService` updated: now injects `GraphRagRetriever` instead of raw `HybridSearchService`

## Memory

- **Package**: `com.agentflow.gateway.memory` (MapperScan covers this)
- `MemoryStore` interface: `add`, `getRecent`, `updateSummary`, `getSummary`, `updateEntityMemory`, `getEntityMemory`
- `SmartMemoryService` implements `MemoryStore`:
  - Short-term: Redis List `mem:short:{sid}`, max 50 items, 4h TTL
  - Long-term summary: MySQL `memory_summaries` table
  - Entity memory: MySQL `memory_entities` table
  - `compress(sessionId)`: triggered when estimated tokens > `trigger-tokens` (2000) → LLM summarize + extract entities → trim Redis to `keep-recent` (10) items
  - `getContextForLLM(sessionId)`: returns "摘要 + 实体 + 最近10条消息" for agent prompt injection
- Configured via `memory.short-term.max-items`, `memory.compression.trigger-tokens`, `memory.compression.keep-recent`
- WorkflowExecutor AGENT node: injects memory context into system prompt before execution, saves user/assistant messages afterward
- DDL: `memory_entries`, `memory_summaries`, `memory_entities` tables in `schema.sql`

## MCP

- `McpClientService` in `service/`: reads `mcp.servers` from YAML → `HttpMcpTransport(sseUrl)` → `DefaultMcpClient` → `toolRegistry.registerMcpClient()`; `@Scheduled` health check every 60s
- `McpServerExporter` in `service/`: `@RestController` at `${agentflow.mcp.export.endpoint}` (default `/mcp/agentflow`), gated by `agentflow.mcp.export.enabled`; `GET /tools` + `POST /execute` with `X-API-Key` auth; only exports `@AgentTool(exportable=true)` tools
- `ToolMarketController` in `controller/`: `GET /api/v1/tools`, `PUT /.../{name}/enable|disable`, `POST /.../upload` (placeholder)
- Frontend: `ToolMarket.vue` at `/tools` — card grid with search, enable/disable toggle, expand details, upload dialog

## Health & Debug

| Controller | Key Endpoints |
|-----------|--------------|
| `HealthCheckController` | `GET /api/v1/health/mcp`, `GET /api/v1/health/engines`, `POST /api/v1/test/route` |
| `DebugPhase2Controller` | `POST /api/v1/debug/graph/extract`, `POST /.../graph/query`, `GET /.../memory/{sid}`, `POST /.../memory/{sid}/compress` |
| `MemoryController` | `GET /api/v1/memory/{sid}/summary`, `GET /.../entities`, `GET /.../status`, `POST /.../compress` |

## Deploy

- Secrets: all API keys/passwords use `${ENV_VAR:default}` in `application.yml`; copy `.env.example` → `.env` and fill real values
- `.gitignore` excludes `.env`, `*.key`, `*.pem`, `application-local.yml`
- `docker-compose.yml`: PostgreSQL(pgvector) + Redis + Neo4j + App, with health checks
- `Dockerfile`: eclipse-temurin:21-jre-alpine
- `deploy.sh`: one-command deploy (check .env → build JAR → start services → run app)