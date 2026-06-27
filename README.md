# ZipRun — AI Reassignment Engine (Backend)

ZipRun runs a fleet of delivery agents. Orders are assigned to agents at the start of a shift —
which works until something goes wrong mid-day: an agent calls in sick, a bike breaks down.
This service is the **reactive reassignment engine**: when an agent goes **offline**, it
automatically detects the stranded orders, uses an LLM (with a deterministic fallback) to
recommend the best reassignment for each, and queues those suggestions for an ops manager to
approve — **no one has to ask for it**.

> The system **proposes; ops disposes.** It never auto-assigns — a human approves every move.

Built with **Java 17 · Spring Boot 3.3 · Spring Data JPA · Validation · Actuator**, on **H2**
(in-memory, zero setup). LLM via **Ollama / Gemini / Groq**.

- Design decisions: [`ADR.md`](./ADR.md)
- Scope & requirements: [`requirements.md`](./requirements.md)
- API collection: [`postman/ZipRun.postman_collection.json`](./postman)
- Frontend (ops UI): separate `ziprun-ui` project (React + Vite)

---

## Quick start (under 5 minutes)

```bash
mvn spring-boot:run
```

Defaults to the `h2` profile (in-memory DB, seeded on startup, dropped on shutdown) and the
`rule-based` routing strategy — so it runs with **no external dependencies**.

| What | Where |
|------|-------|
| App | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 console | http://localhost:8080/h2-console — JDBC `jdbc:h2:mem:ziprun`, user `sa`, blank password |

**Seed data** loads automatically (`src/main/resources/data.sql`): **5 agents** (`AGT-001..005`),
**8 orders** (`ORD-001..008`, all `ASSIGNED`). `AGT-002` & `AGT-004` are `AVAILABLE`; the rest `BUSY`.

To reset state, just restart (the DB is in-memory).

---

## The core flow — what to demo

```
PATCH /agents/{id}/status = OFFLINE     ← an agent drops out
        │  (returns immediately, ~ms)
        ▼  async, off the request path
  find that agent's stranded orders
        ▼
  run the active routing strategy per order  (AI → rule-based fallback)
        ▼
  queue a ReassignmentSuggestion (triggerReason = AGENT_OFFLINE) for each
        ▼
  ops sees them on the next poll  →  PATCH /suggestions/{id} = ACCEPTED  ← the human checkpoint
        ▼
  order reassigned, agent loads rebalanced
```

Try it with curl (rule-based, no LLM needed):

```bash
# 1. Take a busy agent offline — returns instantly
curl -X PATCH localhost:8080/agents/AGT-005/status \
     -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}'

# 2. A moment later, its orders have AGENT_OFFLINE suggestions queued automatically
curl 'localhost:8080/suggestions?status=PENDING'

# 3. Accept one — the order is reassigned to the recommended agent
curl -X PATCH localhost:8080/suggestions/1 \
     -H 'Content-Type: application/json' -d '{"status":"ACCEPTED"}'
```

---

## API reference

All paths are mounted at the brief's bare paths (no `/api/v1` prefix). Errors come back in a
uniform shape (see [Error contract](#error-contract)).

### Orders

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/orders` | Create an order pre-assigned to an agent (the "morning manual assignment") |
| `GET`  | `/orders?status=` | List orders; optional `status` filter (`ASSIGNED` / `REASSIGNMENT_PENDING` / `REASSIGNED` / `DELIVERED`) |
| `POST` | `/orders/{id}/suggest` | Run the **active** routing strategy and queue a suggestion (`triggerReason=INITIAL`) |

```bash
# Create
curl -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"description":"Flowers — Jayanagar to Koramangala","assignedAgentId":"AGT-002"}'
```
```jsonc
// 201 Created
{ "id": "ORD-1A2B3C4D", "description": "Flowers — Jayanagar to Koramangala",
  "assignedAgentId": "AGT-002", "assignedAgentName": "Rahul Verma",
  "status": "ASSIGNED", "createdAt": "2026-06-27T08:01:27.0Z" }
```

### Agents

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/agents` | Roster: id, name, active load, status |
| `PATCH`| `/agents/{id}/status` | Update availability. Transitioning to `OFFLINE` **fires the agentic loop** (async; the call returns immediately) |

```bash
curl -X PATCH localhost:8080/agents/AGT-001/status \
  -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}'   # OFFLINE | AVAILABLE | BUSY
```

### Suggestions

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/suggestions?status=` | List suggestions; optional `status` filter (`PENDING` / `ACCEPTED` / `REJECTED`) |
| `PATCH`| `/suggestions/{id}` | The **human checkpoint** — `ACCEPTED` applies the reassignment; `REJECTED` closes it |

```jsonc
// GET /suggestions?status=PENDING  → each item:
{ "id": 1, "orderId": "ORD-003", "orderDescription": "Pharma — Whitefield to Marathahalli",
  "recommendedAgentId": "AGT-002", "recommendedAgentName": "Rahul Verma",
  "confidence": 0.9, "reasoning": "Rahul Verma has the fewest active orders…",
  "status": "PENDING", "triggerReason": "AGENT_OFFLINE", "strategyUsed": "ai",
  "createdAt": "2026-06-27T08:01:27.0Z" }
```

### Routing config (runtime switch)

| Method | Path | Purpose |
|--------|------|---------|
| `GET`  | `/config/routing-strategy` | Active strategy + the available set |
| `PATCH`| `/config/routing-strategy` | Switch the active strategy at runtime — **no restart** |

```bash
curl -X PATCH localhost:8080/config/routing-strategy \
  -H 'Content-Type: application/json' -d '{"strategy":"ai"}'   # rule-based | ai
```

### Error contract

Every failure returns the same JSON shape and an appropriate status code:

```jsonc
{ "timestamp": "...", "status": 404, "error": "NOT_FOUND",
  "message": "Agent with id 'NOPE' not found", "path": "/orders", "details": [] }
```

`400 VALIDATION_FAILED` (bad body, with field `details`) · `400 BAD_REQUEST` (bad enum/param) ·
`404 NOT_FOUND` (missing entity) · `409 BUSINESS_RULE_VIOLATION` (illegal state transition,
e.g. accepting an already-reassigned order, or an unknown strategy name).

---

## Domain model & state machines

| Entity | Key fields | State machine |
|--------|-----------|---------------|
| **Order** | id, description, assignedAgent, status | `ASSIGNED → REASSIGNMENT_PENDING → REASSIGNED → DELIVERED` |
| **Agent** | id, name, activeOrderCount, status | `AVAILABLE` · `BUSY` · `OFFLINE` |
| **ReassignmentSuggestion** | order, recommendedAgent, confidence (0–1), reasoning, status, triggerReason, strategyUsed | `PENDING → ACCEPTED \| REJECTED` |

Transitions are enforced **inside the entities** (no public setters); illegal moves throw and map
to `409`. Agent/Order use natural-key string IDs (`AGT-001`, `ORD-001`) so they line up with the
seed and with the IDs the LLM echoes back.

**Sprint-2 seams already present** (nullable, unused): `Order.pickupZone/dropoffZone/weightClass/
slaDeadline`, `Agent.currentZone/maxCapacity`. See `ADR-EXT` for the extension points.

---

## How the tasks map to the code

| Task | What | Where |
|------|------|-------|
| **T-1** Domain & API | 3 entities + state machines, 5 endpoints, structured errors, seed | `agent/`, `order/`, `suggestion/`, `common/exception/` |
| **T-2** Pluggable routing | `RoutingStrategy` contract, rule-based impl, runtime-switchable resolver | `routing/` |
| **T-3** AI strategy | LLM gateway, two prompts (initial vs re-plan), validation, fallback | `ai/` |
| **T-4** Agentic loop | offline event → async, idempotent re-planning, off the request path | `agentic/`, `agent/AgentWentOfflineEvent` |

### Routing engine (T-2)

One contract, two implementations, switchable by config at runtime:

- `RoutingStrategy.recommend(RoutingContext)` → ordered recommendations.
- `RuleBasedRoutingStrategy` (`"rule-based"`) — picks the available agent with the fewest active
  orders. Deterministic; also the fallback when AI is down.
- `AiRoutingStrategy` (`"ai"`) — see T-3.
- `RoutingStrategyResolver` injects every strategy, indexes by `name()`, and holds the active one;
  flip it via `PATCH /config/routing-strategy` (no restart). Adding a strategy = implement the
  interface + `@Component`; nothing else changes.

### AI strategy (T-3)

- Two genuinely different prompts: a first-assignment prompt and a **recovery/re-plan** prompt
  (names the offline agent, how many orders are stranded, recovery framing).
- The returned agent id is **validated against the live roster** (hallucinations rejected),
  confidence clamped to `[0,1]`, calls bounded by connect/read timeouts.
- **Resilience:** any LLM failure (timeout, quota, bad JSON, hallucination) falls back to
  rule-based — the suggestion is never dropped. The reasoning is prefixed
  `[AI unavailable — rule-based fallback]` and the event is logged (WARN for expected outages,
  ERROR for bugs/misconfig).

### Agentic loop (T-4)

- `PATCH /agents/{id}/status=OFFLINE` publishes `AgentWentOfflineEvent` **inside its transaction**.
- An `@Async @TransactionalEventListener(AFTER_COMMIT)` runs re-planning on a bounded `replan-`
  thread pool — the PATCH returns in ~milliseconds, re-planning happens separately.
- For each stranded order it runs the active strategy and queues an `AGENT_OFFLINE` suggestion.
- **Idempotent:** skips an order that already has a `PENDING` `AGENT_OFFLINE` suggestion, so the
  same agent flipping offline twice doesn't duplicate.

---

## LLM configuration

Defaults to a **local Ollama** with no API key. The app boots and serves rule-based even if no LLM
is present; switch to `ai` only when a provider is reachable.

```yaml
# application.yml (env-overridable)
llm:
  provider: ${LLM_PROVIDER:ollama}      # ollama | gemini | groq
  api-key:  ${LLM_API_KEY:}             # not needed for ollama; NEVER commit a key
  model:    ${LLM_MODEL:llama3.1}
  base-url: ${LLM_BASE_URL:http://localhost:11434/api}
  connect-timeout-ms: 5000
  read-timeout-ms: 20000
routing:
  strategy: ${ROUTING_STRATEGY:rule-based}   # rule-based | ai
```

To try the AI path locally:

```bash
ollama pull llama3.1 && ollama serve          # in a separate terminal
ROUTING_STRATEGY=ai mvn spring-boot:run        # or flip at runtime via /config/routing-strategy
```

For a hosted provider:

```bash
LLM_PROVIDER=gemini LLM_API_KEY=$KEY LLM_MODEL=gemini-1.5-flash \
LLM_BASE_URL=https://generativelanguage.googleapis.com ROUTING_STRATEGY=ai mvn spring-boot:run
```

---

## Testing

```bash
mvn test        # 62 tests: domain/state-machine units, routing, AI (stubbed LLM), agentic loop, API integration
```

Notable coverage: state-machine guards, rule-based ranking, resolver switch + startup validation,
AI happy-path + every fallback mode (no network), the agentic loop's idempotency, and full
MockMvc API flows (`@SpringBootTest`, `h2` profile).

## Build

```bash
mvn clean package
```

---

## Project structure

```
src/main/java/com/ziprun/
├── agent/         Agent, AgentStatus, AgentService, AgentController, AgentWentOfflineEvent, dto/
├── order/         Order, OrderStatus, WeightClass, OrderService, OrderController, dto/
├── suggestion/    ReassignmentSuggestion, SuggestionStatus, TriggerReason, …Service, …Controller, dto/
├── routing/       RoutingStrategy, RoutingContext, RuleBasedRoutingStrategy,
│                  RoutingStrategyResolver, RoutingConfigController, dto/
├── ai/            LlmClient, LLMGateway, RoutingPromptFactory, AiResponseParser, AiRoutingStrategy
├── agentic/       AgentOfflineListener (async), ReplanningService, ReplanSummary
└── common/        config/ (CORS, Async), exception/ (GlobalExceptionHandler, ErrorResponse)
```

CORS is open to the UI dev servers (`http://localhost:5173` React/Vite, `http://localhost:4200`
Angular).
