# ZipRun — Architecture Decision Record

> Decisions captured **as they were made**. Split into **Major ADRs** (the load-bearing
> architectural calls + the brief's required entries) and **Minor / LLM-driven ADRs**
> (smaller, mostly tactical decisions). Format per entry:
> **Context → Options considered → Decision → Tradeoffs accepted.**

---

# Major ADRs

### ADR-T1 — Where routing logic lives (decision vs selection vs orchestration)

**Context.** Routing could collapse into one service holding the picking logic, strategy
selection, suggestion persistence, and (in T-4) event publishing — exactly the god-service
the brief warns against.

**Options considered.**
(a) One `SuggestionService` that does everything.
(b) Three boundaries: routing **decision** in `RoutingStrategy` implementations, strategy
**selection** in `RoutingStrategyResolver`, and **orchestration only**
(run strategy → enforce invariant → persist) in `SuggestionService`.
(c) Single orchestration Service fetching routing strategy from db.

**Decision.** (b). Strategies are pure decision logic with no persistence dependency; the
resolver owns selection; the service orchestrates. `createSuggestion(order, trigger)` is the
single orchestration entry point, reused by the HTTP path now and the T-4 async loop later.

**Tradeoffs accepted.** More types and a layer of indirection; a reader must know the
decision/selection/orchestration split to trace a request end to end -> lower consistency/persistence compared to db strategy 

### ADR-T2 — Runtime strategy switchability via an auto-wired resolver

**Context.** The active strategy must be switchable without a restart, called identically
from an HTTP endpoint and (T-4) an async handler, and sprint 2 will add a third
(`ZoneAffinityStrategy`).

**Options considered.**
(a) `@Qualifier` + config property — needs a restart to switch.
(b) Manual factory with a `switch` — must be edited for every new strategy.

**Decision.** (c) — `RoutingStrategyResolver`. The active name initialises from
`routing.strategy` and can be flipped at runtime via `PATCH /config/routing-strategy`
(`activeName` is `volatile`, so the async loop thread sees the change). Adding
`ZoneAffinityStrategy` = implement the interface + annotate `@Component`; selection code is
untouched. The configured name is validated at startup — construction fails fast instead of
500-ing on first request.

**Tradeoffs accepted.** Less explicit than a factory (Spring auto-populates the strategy
list by reflection); the active strategy is runtime state, so a bad value is a runtime error
— mitigated by validating against registered names on both the startup and the switch paths.

**Persistence & distribution (deliberate single-instance choice).** The active strategy is
held **in memory** (`volatile` field); `routing.strategy` (config/env) is the durable source
of truth and the value on every restart. A runtime `PATCH` is therefore **intentionally
ephemeral** — it reverts to config on restart. We considered persisting it to the database
and rejected it for this scope:
- in a **distributed / multi-instance** deployment, in-memory state is
  genuinely insufficient: a `PATCH` lands on one node, so only that instance switches and the
  fleet routes inconsistently. But a DB row alone does **not** fix this — each node must cache
  the value (the routing call is a hot path; we can't read the DB per call), so a write by one
  node is invisible to others until they re-read. The real problem there is
  **change-propagation / cache-invalidation**, not persistence.
- Possible multi-instance evolution (out of scope today): DB as source of truth + a
  bounded-TTL cache (eventual convergence), or DB + push propagation (Redis/Kafka pub-sub,
  Postgres `LISTEN/NOTIFY`, 

For a single-instance, 5-hour scope, config + in-memory is the right call (matches 12-factor
operational toggles and the brief's worked ADR), with the above as the documented migration
path.

### ADR-T3 — Routing contract carries a `RoutingContext` (situation), not bare (order, agents)

**Context.** The AI strategy needs the *trigger context* — is this a first assignment or a
recovery from an agent going offline? — to reason and to pick the right prompt. The T-2
contract was `recommend(Order, List<Agent>)`, which can't express that.

**Options considered.**
(a) Keep `(order, agents)` and infer the situation from order state — unreliable (both paths
leave the order `REASSIGNMENT_PENDING`).
(b) Evolve the contract to `recommend(RoutingContext)`, where the context optionally carries
recovery details (the offline agent + stranded count).

**Decision.** (c). This is the brief's "design the interface with the AI in mind from the
start." The rule-based strategy ignores `recovery`; the AI strategy switches on it. Package
direction stays one-way — `RoutingContext` lives in `routing` (depends only on order/agent),
and the whole `ai` package depends on `routing`, never the reverse. A sprint-3 SLA-breach
trigger becomes a new `Recovery` flavour with no interface change.

**Tradeoffs accepted.** One more type, and a one-time refactor of the T-2 callers/tests.
`triggerReason` (persisted on the suggestion) and `recovery` (routing input) are slightly
redundant — the service derives the former from the latter to keep them consistent.

### ADR-T4 — LLM resilience: every failure mode collapses to a rule-based suggestion

**Context.** LLM calls fail in distinct ways — timeout, quota/HTTP error, malformed JSON,
hallucinated agent id. The reassignment flow must stay healthy regardless; in the T-4 async
path especially, a failed AI call must still produce a suggestion, never a silent drop.

**Options considered.**
(a) Put fallback *inside* `AiRoutingStrategy` — it owns its own resilience and delegates to an
injected `RuleBasedRoutingStrategy` on any failure.
(b)Let LLM errors be an error and show no suggestion instead of a rule based routing. 

**Decision.** (b). One `try/catch` around build-prompt → call → parse → validate. The
recommended agent id is validated against the live roster (hallucinations rejected); confidence
is clamped to [0,1]; gateway timeouts are bounded (connect 5s / read 20s) so a hung LLM can't
block the caller. On any failure it logs at WARN with the cause and returns rule-based
recommendations whose reasoning is prefixed `[AI unavailable — rule-based fallback]`. Verified
live by pointing at a dead LLM URL: the suggest call still returned 200 with a rule-based pick.

**Tradeoffs accepted.** `strategyUsed` records the *selected* strategy (`"ai"`) even when it
fell back, so that field alone doesn't reveal the fallback — the prefixed reasoning and the log
do. Ops did not want rule-based strategy but still showed one.

**Catch granularity.** Fallback is universal (no LLM failure escapes), but the catch is split by
intent so bugs aren't masked as outages: expected failures (`AiRoutingException`,
`RestClientException` — timeout/quota/HTTP/bad output/hallucination) log at **WARN** (routine);
anything else (e.g. a code bug or `Unknown provider` misconfig) logs at **ERROR** with a stack
trace — both still fall back. `Error` (OOM etc.) is intentionally not caught.

### ADR-T5 — Loop triggered by a domain event after commit, off the request path

**Context.** `PATCH /agents/{id}/status` must return immediately, yet flipping an agent OFFLINE
has to kick off re-planning of that agent's orders. The trigger should fire because state
*changed*, not because a timer ticked.

**Options considered.**
(a) Scheduled poller scanning for offline agents — not agentic (timer, not event), adds latency
and wasted scans.
(b) Synchronous re-plan inside the PATCH handler — blocks the response, couples status update to
routing + persistence.
(c) `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`.

**Decision.** (c). `AgentService` publishes `AgentWentOfflineEvent` *inside* its transaction,
only on the transition into OFFLINE. The listener runs `AFTER_COMMIT` (so the OFFLINE status and
the agent's orders are committed and visible) and `@Async` on a bounded `replan-` pool (so the
PATCH returns at once — measured ~3–40 ms while re-planning ran separately, incl. a ~14 s AI
pass). This is the observe→reason→act→checkpoint loop: observe the OFFLINE event, reason about
*which* orders are stranded, act by queuing suggestions, checkpoint at ops approval.

**Tradeoffs accepted.** After-commit async work runs in its own transaction, so a failure there
can't roll back the status change (correct — the agent *is* offline regardless). Such failures
are surfaced via logs, not propagated to the caller (the caller is long gone). At-least/at-most
delivery isn't guaranteed across a crash mid-replan; idempotency (ADR-T4.3) makes a re-fire safe.

### ADR-EXT — Extensibility: the sprint-2 `ZoneAffinityStrategy` seam (shown, not claimed)

**Context.** The brief asks us to pick one sprint-2/3 roadmap item and point to the exact
extension seam in the code. Chosen item: **sprint-2 `ZoneAffinityStrategy`** — "routing starts
preferring agents already near the pickup zone," added as a third strategy.

**The seam (concrete code pointers).** Adding it touches **no existing routing code** — it's
implement-and-register:
1. **Implement the contract** — `routing/RoutingStrategy.java`:
   `List<RoutingRecommendation> recommend(RoutingContext context)`. A new
   `routing/ZoneAffinityStrategy.java` annotated `@Component` with `name() == "zone-affinity"`.
2. **Registration is automatic** — `routing/RoutingStrategyResolver.java` injects
   `List<RoutingStrategy>` and indexes them by `name()`. A new `@Component` appears in that map
   with zero edits to the resolver, controller, or services. Activate at runtime via
   `PATCH /config/routing-strategy {"strategy":"zone-affinity"}`.
3. **The data it needs already exists** — `Agent.currentZone` and
   `Order.pickupZone`/`dropoffZone` are already nullable columns (see ADR-T1.5), and
   `RoutingContext` already hands the strategy the order + the available-agent snapshot. So the
   new strategy reads `context.order().getPickupZone()` and `agent.getCurrentZone()` —
   no schema migration, no contract change.
4. **Both callers get it for free** — the HTTP path (`SuggestionService.createSuggestion`) and
   the async re-plan (`agentic/ReplanningService`) both resolve the active strategy through the
   same `RoutingStrategyResolver.active()`, so a new strategy works identically for on-demand
   suggestions and the agentic loop.

**Decision.** Keep the strategy interface + auto-wired resolver as the single extension point;
keep sprint-2 fields as activated-but-unused placeholders. Adding `ZoneAffinityStrategy` is a
new file + a bean annotation.

**Tradeoffs accepted.** The placeholder columns sit null until sprint 2 (negligible cost). The
resolver's by-name registration is implicit (Spring reflection) rather than an explicit
registry list — the price of zero-edit extensibility.

### ADR-EXC — Deliberate exclusions (priority calls, not time excuses)

**Context.** A 5-hour solo budget on a backend/systems screen forces explicit scoping. Each
exclusion below is a priority decision with a rationale.

**What we chose not to build, and why.**
- **SSE streaming bonus (T-3 +5).** Deferred because it's a *visibility* enhancement on top of
  an already-working suggestion, whereas AI resilience and the agentic loop are *correctness*
  requirements. Streaming tokens earns points only after the thing that produces them is solid.
- **UI ceiling — full dispatch board, SLA countdown, agent-load viz, zone roster (+8).**
  Deferred because the agentic loop is a correctness requirement and the board is a visibility
  enhancement; a clean floor that demonstrably shows the re-plan badge appearing is worth more
  than an ambitious board that costs backend depth (the brief says this outright).
- **Auto-derived BUSY / capacity enforcement.** Status is kept as an *external* presence signal
  (the brief's scenario: an agent "calls in sick"), not inferred from order count. "BUSY = at
  capacity" needs `maxCapacity` (a sprint-2 field we stubbed), so deriving it now would conflate
  availability with load. Deferred to where capacity actually lives.
- **Durable / distributed routing-strategy state.** Single-instance in-memory chosen
  deliberately; multi-instance propagation is a change-propagation problem, not a persistence
  one, and out of scope (see ADR-T2.2).
- **Concurrency-hardening the re-plan idempotency** (partial unique index / per-agent
  serialization). The TOCTOU race only bites under overlapping re-plans of the *same* agent on
  multiple instances; single-instance impact is a duplicate card the state machine still
  blocks. Documented as the production fix (ADR-T4.3) rather than built.
- **Auth, multi-tenancy, Postgres-prod profile, containerization/CI.** Outside the brief's
  scope; H2 in-memory keeps the "runs in under 5 minutes" promise.

**Tradeoffs accepted.** We leave ~13 bonus points (SSE + ceiling) on the table by choice, in
exchange for a correct, well-tested core loop and an honest ADR — the higher-weighted areas.

---

# Minor / LLM-driven ADRs

## T-1 · Domain model & API

### ADR-T1.1 — Natural-key string IDs for Agent and Order

**Context.** Entities need identifiers. The seed script uses human-readable keys
(`AGT-001`, `ORD-001`), and in T-3 the LLM will echo an agent id back that we must match
against our roster.

**Options considered.**
(a) Auto-increment `Long` surrogate keys.
(b) Natural-key `String` IDs (`AGT-001`) assigned by seed or generated in the brief's format.

**Decision.** (b) for `Agent` and `Order`. They line up 1:1 with the seed and, importantly,
with the identifiers the AI returns — validation in T-3 becomes a direct `findById`.
`ReassignmentSuggestion`, which is never seeded and only system-created, keeps an
auto-increment `Long`.

**Tradeoffs accepted.** Application-assigned string keys mean no DB-generated uniqueness for
those tables and slightly larger indexes than a `Long`. Acceptable at this scale, and the
demo/LLM ergonomics outweigh it.

### ADR-T1.2 — Rich domain model: transitions through guarded methods, not setters

**Context.** Three entities each have a state machine (Order
`ASSIGNED→REASSIGNMENT_PENDING→REASSIGNED→DELIVERED`, Agent `AVAILABLE/BUSY/OFFLINE`,
Suggestion `PENDING→ACCEPTED|REJECTED`). The brief stresses that transitions matter more
than the field list.

**Options considered.**
(a) Anemic entities with public setters; transition rules enforced in services.
(b) Rich entities: no public setters, transitions via methods (`markReassignmentPending`,
`reassignTo`, `accept`/`reject`) that guard legality.

**Decision.** (b). Illegal transitions throw `BusinessRuleException` (HTTP 409) from inside
the entity, so the invariant lives in exactly one place and can't be bypassed by a new
caller. This also gives a free correctness net: accepting a suggestion for an
already-reassigned order fails at `reassignTo` (verified live).

**Tradeoffs accepted.** Slightly more ceremony than setters; tests must drive entities
through valid sequences. Worth it for centralised invariants.

### ADR-T1.3 — Uniform structured error contract via `@RestControllerAdvice`

**Context.** API correctness is scored on "right status codes, errors in a structured
shape." Controllers/services shouldn't each assemble error bodies.

**Options considered.**
(a) Per-controller try/catch returning ad-hoc bodies.
(b) One `GlobalExceptionHandler` mapping domain exceptions → a single `ErrorResponse`
record (timestamp, status, code, message, path, details).

**Decision.** (b). `ResourceNotFoundException`→404, `BusinessRuleException`→409 (illegal
transitions), bean-validation→400 with field details, bad enum/path→400, fallback→500.
Services just throw; mapping is centralised.

**Tradeoffs accepted.** A catch-all `Exception`→500 can mask an unmapped case as a generic
error; mitigated by logging the cause server-side.

### ADR-T1.4 — Endpoints mounted at the brief's bare paths (no `/api/v1`)

**Context.** Graders curl specific paths and the UI is built to a contract.

**Options considered.** (a) Versioned prefix `/api/v1/...`; (b) the brief's literal paths
(`/orders`, `/agents/{id}/status`, `/suggestions/{id}`, `/orders/{id}/suggest`).

**Decision.** (b) — match the brief verbatim so curls and the UI line up with zero
translation. Added `GET /agents` and `GET /suggestions?status=` (not required by T-1) to
feed the UI floor.

**Tradeoffs accepted.** No API versioning seam. For a 5-hour scope with a fixed contract,
versioning is premature.

### ADR-T1.5 — Sprint-2 fields as nullable placeholders now

**Context.** Sprint 2 adds zone, capacity, weight class, SLA. The brief notes "a nullable
placeholder now costs nothing; a migration in sprint 2 costs a sprint."

**Options considered.** (a) Add them only when sprint 2 arrives; (b) add nullable columns
now (`Order.pickupZone/dropoffZone/weightClass/slaDeadline`, `Agent.currentZone/maxCapacity`).

**Decision.** (b). They sit unused and documented as seams. `slaDeadline` in particular is
the hook the sprint-3 proactive loop will fire on.

**Tradeoffs accepted.** A handful of always-null columns until sprint 2. Negligible cost;
avoids a schema migration later.

## T-2 · Pluggable routing engine

### ADR-T2.3 — One PENDING suggestion per order (supersede-on-create)

**Context.** Nothing structurally stops multiple `PENDING` suggestions for one order
(on-demand + an agent-offline re-plan, or repeated `POST /suggest`). The brief's idempotency
requirement (AGT-4) literally only dedups `AGENT_OFFLINE`+`PENDING`. The UI describes "the
current suggestion" (singular) per order.

**Options considered.**
(a) Trigger-scoped dedup only (the brief-literal AGT-4 guard).
(b) Enforce at-most-one `PENDING` per order: creating a new suggestion supersedes any
existing `PENDING` one; accept also auto-rejects siblings.

**Decision.** (b). `createSuggestion` supersedes the prior `PENDING` (marks it `REJECTED`)
before persisting the new one, giving exactly one active card per order and generalising
AGT-4. The state-machine guard in `Order.reassignTo` is the backstop against
double-reassignment (a stale accept → 409, verified live).

**Tradeoffs accepted.** Superseded suggestions reuse the `REJECTED` status rather than a
distinct `SUPERSEDED`, so "ops-rejected" and "system-superseded" aren't told apart by status
alone. Keeps the brief's 3-state machine intact; the `triggerReason`/history still carries
context if we need to distinguish later.

### ADR-T2.4 — `POST /orders/{id}/suggest` moves the order to REASSIGNMENT_PENDING

**Context.** A suggestion only makes sense for an order awaiting reassignment, and the UI
floor lists orders *in* `REASSIGNMENT_PENDING` with their suggestion inline.

**Options considered.** (a) Create the suggestion but leave the order `ASSIGNED`;
(b) move the order to `REASSIGNMENT_PENDING` as part of creating the suggestion.

**Decision.** (b) — `createSuggestion` calls `markReassignmentPending()` first, so the order
surfaces in the pending list with its recommendation. Candidate agents exclude the order's
current holder (recommending the same agent is pointless).

**Tradeoffs accepted.** Asking for a suggestion mutates order state, not just reads. That's
intended here (a suggestion *is* the start of a reassignment), but it means "preview a
suggestion without committing the order to pending" isn't possible without a new flag.

## T-3 · AI routing strategy

### ADR-T3.2 — Two genuinely different prompts (initial vs re-plan)

**Context.** The brief is explicit: an initial assignment and a recovery are different
situations, and "the same document with one field added" is wrong. Both walkthrough points.

**Options considered.** (a) One template with a conditional line; (b) two purpose-built
prompts from a `RoutingPromptFactory` sharing only the JSON output contract.

**Decision.** (b). The initial prompt is a normal "pick the best available agent." The re-plan
prompt is a *situation report*: names the offline agent, states how many orders are stranded,
declares prior assignments void, and frames it as time-sensitive recovery. Shared
`OUTPUT_CONTRACT` keeps the parser single. (Tested: the two prompts are asserted non-equal and
the re-plan one carries the offline agent + count.)

**Tradeoffs accepted.** Two templates to maintain; if the output schema changes it must change
in one shared constant (it does). Prompt text isn't externalised to config — fine at this scope.

### ADR-T3.4 — `LlmClient` abstraction + provider gateway; Ollama via native `/api/chat`

**Context.** Addendum B supplies provider wire formats. We need it testable (no HTTP in unit
tests), keyless-local-friendly, and the default was changed to a local Ollama.

**Options considered.** (a) Use the `LLMGateway` class directly everywhere; (b) extract an
`LlmClient` interface the gateway implements, so the strategy depends on the abstraction and
tests substitute a one-line stub.

**Decision.** (b). `AiRoutingStrategy` depends on `LlmClient`; all 7 strategy tests inject a
lambda stub — no mocking framework, no network. Default provider is **Ollama** (`llama3.1`, no
API key). Because the configured base-url is `…/api`, the Ollama branch calls the **native**
`/api/chat` with `stream:false` (parsing `message.content`) rather than the OpenAI-compatible
path the addendum used for Ollama; Groq/Gemini keep their formats. Keys come from env only.

**Tradeoffs accepted.** A second response shape to parse for Ollama-native vs OpenAI-compatible.
On-demand `POST /orders/{id}/suggest` does wait on the LLM (bounded by timeout) — acceptable
because it's an explicit user request; the latency-sensitive path (`PATCH …/status`) is async in
T-4 and never blocks. Defaults let the app boot and serve rule-based even with no LLM present.

## T-4 · Agentic re-planning loop

### ADR-T4.2 — Thin async listener delegating to a transactional worker

**Context.** The async/event concern and the transactional re-planning logic have different
lifecycles, and Spring's `@Transactional` is proxy-based (self-invocation within one bean is not
intercepted).

**Options considered.** (a) One method carrying `@Async` + `@TransactionalEventListener` +
`@Transactional` together; (b) split: `AgentOfflineListener` (async/event) delegates to
`ReplanningService.replan(...)` (`@Transactional`).

**Decision.** (b). The listener is thin — observe, delegate, log the `ReplanSummary`, last-resort
catch. `ReplanningService.replan` is a plain synchronous `@Transactional` method on a separate
bean, so the transaction proxy actually applies and the loop logic is **directly unit-testable**
without async timing (the deterministic tests call it straight; a separate polling test covers the
async wiring).

**Tradeoffs accepted.** Two classes instead of one. Worth it for the testability and the clean
separation of "when it runs" from "what it does."

### ADR-T4.3 — Idempotency + per-order isolation, and recovery context for the AI

**Context.** The same agent can flip offline twice quickly, or overlapping triggers can target
the same order — a naive loop duplicates suggestions. And one order's failure shouldn't abort the
whole batch.

**Options considered.** (a) Recreate suggestions every trigger; (b) guard each order with a query
for an existing open re-plan suggestion before creating one.

**Decision.** (b). Before creating, skip if a PENDING `AGENT_OFFLINE` suggestion already exists
for the order (`existsByOrderAndTriggerReasonAndStatus`) — verified live: a re-flip logged
`created=0, skipped=3`. Each order is handled in its own try/catch so one failure is logged and the
loop continues (no silent drop); AI failures are already absorbed by the strategy's rule-based
fallback, so a suggestion still surfaces. The loop passes a `RoutingContext.Recovery` (the offline
agent + stranded-order count) so the AI strategy uses its re-plan prompt — confirmed live, the
model's reasoning referenced absorbing the failed agent's load.

**Tradeoffs accepted.** The idempotency check is a per-order query inside the loop (N+1-ish for a
big batch) — negligible at fleet scale and clearer than a batch pre-fetch. A re-plan that fails
mid-order leaves that order `REASSIGNMENT_PENDING` without a suggestion until the next trigger.

**Concurrency caveat (known limitation).** The skip-or-create check is check-then-act (TOCTOU).
The method's counters are thread-safe (method-local, per-stack), but the *idempotency guarantee*
is not race-proof: if the same agent flips offline twice and the two `@Async` re-plans overlap,
both run in separate READ_COMMITTED transactions that can't see each other's uncommitted insert, so
both can create a suggestion for the same order. Single-instance impact is small (worst case: a
duplicate card; the state machine still blocks double-reassignment). Production fix: a partial
unique index on `(order_id)` where `status='PENDING'` (insert fails atomically → treat as skipped),
or per-agent serialization of the re-plan executor.

### ADR-T4.4 — The human checkpoint: queue, never auto-assign (AGT-2)

**Context.** The loop could auto-apply the best reassignment. The brief is explicit that it
should propose, and ops disposes.

**Options considered.** (a) Auto-assign on high confidence; (b) always queue a PENDING suggestion
and let ops accept/reject.

**Decision.** (b). The loop only ever creates PENDING suggestions; the sole place an order changes
hands is `PATCH /suggestions/{id}` = ACCEPTED (the checkpoint), guarded by the order state machine.
Auto-assigning isn't "more complete" — it's a different system with the human out of the loop.

**Tradeoffs accepted.** A reassignment waits on a human, so mean-time-to-reassign includes ops
reaction time. Acceptable and intentional now. When we'd revisit: auto-accept above a confidence
threshold or on imminent SLA breach (sprint 3) — the checkpoint is one method, so that policy would
live in one place.

## T-5 · Ops interface

### ADR-T5.1 — React (Vite) over Angular; floor over ceiling

**Context.** The brief allows React 18 or Angular 17 and weights the UI lightly (12 pt floor,
+8 ceiling) versus the backend. The UI's job is to make the agentic loop visible.

**Options considered.** (a) Angular 17; (b) React + Vite. And scope: floor-only vs chase the
ceiling (dispatch board, SLA countdown, load viz).

**Decision.** React + Vite (the existing scaffold is React 19 — newer than the brief's 18, same
model). Built the **floor** deliberately and stopped: reassignment queue with verbatim reasoning,
Accept/Reject, the re-plan badge, agent roster with status pills, polling + manual refresh, loading
and error states. No ceiling, no SSE.

**Tradeoffs accepted.** Pass on the ceiling points. Per the brief's own guidance, "a clean
functional floor beats an ambitious ceiling that cost you the backend" — and this is a
backend/systems screen. A polished floor that demonstrably shows the badge appearing is the higher-
value choice.

### ADR-T5.2 — Queue driven by pending suggestions; re-plan badge is the headline signal

**Context.** The floor asks for "orders in REASSIGNMENT_PENDING with the current suggestion shown
inline." With the one-PENDING-per-order invariant (ADR-T2.3), a pending suggestion *is* the unit of
work.

**Options considered.** (a) Fetch pending orders, then fetch each order's suggestion; (b) fetch
`GET /suggestions?status=PENDING`, which already carries `orderId`/`orderDescription` inline.

**Decision.** (b) — one call drives the whole queue, no N+1 from the client. The `triggerReason`
field renders as a badge (`AGENT_OFFLINE` → "AGENTIC RE-PLAN", `INITIAL` → "MANUAL"); the badge
logic is a pure helper (`triggerBadge`) so it's unit-tested in isolation. Reasoning is rendered
verbatim. A "Set offline" action per agent triggers the loop from the UI so the badge appearing is
demonstrable without leaving the page.

**Tradeoffs accepted.** The queue shows orders that have a suggestion, not every
`REASSIGNMENT_PENDING` order — identical in practice given the invariant, but if a re-plan ever
failed to produce a suggestion that order wouldn't appear (the backend logs that case).

### ADR-T5.3 — Polling for freshness; resilient fetch with non-blocking errors

**Context.** Suggestions appear asynchronously after an agent goes offline, so the UI must update
without a reload. Needs loading + error states.

**Options considered.** (a) Manual refresh only; (b) WebSocket/SSE push; (c) interval polling +
manual refresh.

**Decision.** (c). 3s polling (toggleable) plus a Refresh button. Errors surface in a banner but
don't wipe the last good data, and silent background polls don't flip the whole screen into a
loading state — only the first load does. Mutations (accept/reject/set-offline) disable just the
affected row while in flight.

**Tradeoffs accepted.** Polling has up to ~3s latency and makes idle requests — fine at this scale
and far simpler than a push channel (SSE was explicitly descoped with the bonus). The set-offline
handler adds a one-off follow-up poll (~700ms) so the first async suggestions land promptly without
waiting for the next tick.
