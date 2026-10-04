# Prism — LLM Gateway and Semantic Cache

A production-shaped, OpenAI-compatible LLM gateway implemented as three independently
deployable Java 17 / Spring Boot microservices, built for the Airtribe "Prism" capstone brief.

```
prism-gateway-service   :8080   the hot path — auth, rate limit, budget, routing, streaming,
                                 provider adapters, retries/failover, metering
prism-cache-service     :8081   semantic response cache (embeddings + LSH nearest-neighbour)
prism-usage-service     :8082   cross-tenant metering/analytics ingestion + reporting
prism-common                    shared DTOs and the custom exception hierarchy
prism-ops-console               single static HTML page (vanilla JS) for demoing everything
```

## Why three services, not one

The gateway is the only service on the client-facing latency path, so it stays as lean as
possible: authenticate, rate-limit, route, dispatch, stream, log. Semantic caching needs its
own scaling profile (CPU for embeddings, memory for the vector index, its own storage) and can
fail without taking the gateway down with it — so it's a separate service the gateway calls
over HTTP and **fails open** on any error. Usage analytics is a classic "many small writes,
occasional big reads for reporting" workload that benefits from its own database and its own
deploy cadence, decoupled from request-handling entirely; the gateway keeps a *local* copy of
its own request log purely so `/v1/usage/summary` never has a cross-service call on its
critical path, and asynchronously forwards the same events to `prism-usage-service` for
platform-wide reporting. That local-vs-central duplication is a deliberate trade-off, not an
oversight — see "Known limitations" below.

## Quick start

```bash
# 1. Bring up Postgres + all three services
docker compose up --build

# 2. Point the ops console at it
open prism-ops-console/index.html   # or just double-click the file (local-demo CORS is enabled)
#   Gateway URL: http://localhost:8080
#   Usage URL:   http://localhost:8082
#   Virtual key: prism-sk-alpha-0001   (see prism-gateway-service/src/main/resources/data/seed_keys.json)

# 3. Verify
python3 scripts/smoke_test.py
python3 scripts/load_test.py --key prism-sk-beta-0002 --concurrency 50 --requests 200
python3 scripts/routing_eval.py
```

Running locally without Docker: each module is a standalone Spring Boot app
(`mvn -pl prism-gateway-service -am spring-boot:run`, etc.) — point each at a local Postgres
via the `SPRING_DATASOURCE_*` env vars in each module's `application.yml`, or run against H2
by setting `spring.datasource.url=jdbc:h2:mem:prism` for a zero-install trial (Flyway targets
Postgres syntax by default; H2's `MODE=PostgreSQL` compatibility mode, as used in the test
profile, covers everything this schema uses).

> **Note on this environment:** this project was authored and hand-reviewed in a sandbox with
> no access to Maven Central, so it has not been machine-compiled here. The code follows
> standard Spring Boot 3.3 / Java 17 idioms throughout; run `mvn -q -DskipTests package` from
> the repo root on a machine with normal internet access to build it.

## Seeded data (preserved exactly, per the spec)

| File | Purpose |
|---|---|
| `prism-gateway-service/src/main/resources/data/model_pricing.json` | price-per-1k-tokens for each mock model |
| `prism-gateway-service/src/main/resources/data/seed_keys.json` | demo virtual keys (see table below) |
| `prism-gateway-service/src/main/resources/data/gateway_config.sample.json` | alias → fallback-chain map, provider timeout/retry config, cache threshold/TTL |

| Key | Alias | Allowed models | RPM | Monthly budget | Demo purpose |
|---|---|---|---|---|---|
| `prism-sk-alpha-0001` | team-alpha | fast, smart, auto, concrete models | 60 | $50.00 | main demo key |
| `prism-sk-beta-0002` | team-beta | fast, auto only | 20 | $5.00 | model-not-allowed + rate-limit demo |
| `prism-sk-lowbudget-0003` | team-lowbudget | fast, smart, auto | 60 | $0.01 | budget-exhausted demo |

## API

### `POST /v1/chat/completions`
OpenAI-compatible. `Authorization: Bearer <virtual key>`.

```json
{ "model": "auto", "messages": [{"role": "user", "content": "..."}], "stream": false }
```

`model` accepts a concrete model id (`mock-fast-v1`), a static alias (`fast`, `smart`), or the
dynamic `auto` alias (smart-routed by the difficulty classifier). Every response — streaming
or not — carries the header contract:

| Header | Meaning |
|---|---|
| `x-prism-provider` | which upstream actually served this request (or `cache`) |
| `x-prism-cache` | `HIT` or `MISS` |
| `x-prism-fallback` | `true` if the primary model in the chain failed and a fallback served it |
| `x-prism-cost-usd` | computed cost for this request |

For `"stream": true`, the response is SSE: token deltas as `chat.completion.chunk`-shaped JSON.
It carries the same four response-header names up front (`x-prism-provider` is the initially
selected model and `x-prism-cost-usd` is `pending`); a final `prism-meta` named event provides
the authoritative provider, fallback, cache and cost values, followed by a final chunk with
`finish_reason: "stop"` and a terminating `data: [DONE]`.

### `GET /v1/usage/summary` / `GET /v1/usage/requests`
Scoped to the authenticated key. Backs the ops console's "Usage · this key" panel.

### `GET /usage/report?days=7` (prism-usage-service)
Platform-wide spend and per-provider breakdown, independent of any single tenant.

### `GET /admin/providers` / `POST /admin/providers/{provider}/toggle?healthy=false`
Operator endpoints (intentionally outside virtual-key auth) used to simulate an outage for the
failover demo.

### Error envelope
Every rejection uses the same shape with a stable, documented `code`:

```json
{ "error": { "code": "BUDGET_EXHAUSTED", "message": "...", "type": "PAYMENT_REQUIRED", "retryAfterMillis": null } }
```

| Code | HTTP | Cause |
|---|---|---|
| `INVALID_VIRTUAL_KEY` | 401 | missing/unknown/revoked key |
| `MODEL_NOT_ALLOWED` | 403 | key's allowlist doesn't include the requested model/alias |
| `RATE_LIMITED` | 429 | requests-per-minute exceeded (`Retry-After` header set) |
| `BUDGET_EXHAUSTED` | 402 | monthly cost budget would be exceeded |
| `VALIDATION_FAILED` | 400 | request body failed bean validation |
| `UNKNOWN_MODEL_ALIAS` | 400 | model/alias not configured on this gateway |
| `PROVIDER_TIMEOUT` | 504 | upstream exceeded its configured timeout |
| `PROVIDER_UNAVAILABLE` | 502 | upstream returned an error |
| `ALL_PROVIDERS_EXHAUSTED` | 503 | primary + every fallback in the chain failed |

## Architecture inside the gateway

```
request
  │
  ▼
VirtualKeyAuthFilter        (servlet filter: key lookup + token-bucket RPM check)
  │
  ▼
ChatCompletionController    (bean-validates body)
  │
  ▼
RoutingService               allowlist check → resolve alias/auto → RoutingPlan (primary + fallback chain)
  │
  ▼
CacheClientService  ───────► prism-cache-service  (embed → LSH lookup; fails open on error/timeout)
  │  hit                                    │ miss
  ▼                                         ▼
respond, log, done              BudgetService.reserve()  (atomic conditional UPDATE — see below)
                                             │
                                             ▼
                                 ProviderDispatchService
                                   for model in [primary, ...fallbacks]:
                                     circuit breaker check → retry w/ exponential backoff → next on failure
                                             │
                                             ▼
                                 BudgetService.trueUp()   CacheClientService.storeAsync()
                                             │
                                             ▼
                                 UsageLoggingService.enqueue()  (bounded queue → batched async DB write)
                                             │
                                             ▼
                                 respond with header contract
```

## Design patterns used, and why

| Pattern | Where | Why here specifically |
|---|---|---|
| **Adapter** | `ProviderAdapter` / `MockProviderAdapter` | New provider = new adapter class; routing, retry, streaming, metering code never changes |
| **Template Method** | `AbstractProviderAdapter` | Every adapter gets uniform timeout handling + error translation for free |
| **Factory** | `ProviderAdapterFactory` | O(1) model → adapter resolution, open to new adapters without touching the factory |
| **Strategy** | `RoutingService` (alias/auto/direct-model resolution), `EmbeddingService` interface in the cache service | Routing algorithm and embedding algorithm are both swappable without touching their callers |
| **Chain of Responsibility** | `ProviderDispatchService#tryChain` / `#streamChain` | Walks primary → fallback chain, each link deciding to handle or pass on the request |
| **Circuit Breaker** | `SimpleCircuitBreaker` | Stops paying a full provider timeout on every request while that provider is known-down |
| **Builder** | `RequestLog`, Lombok `@Builder` throughout | Readable construction of the wide, mostly-optional-field log row |
| **Repository** | every `*Repository` interface | Persistence isolated behind Spring Data, atomic budget charge expressed as one query |
| **DTO** | `prism-common/dto/*` | Wire contracts (records, immutable) kept separate from JPA entities |
| **Producer/Consumer** | `UsageLoggingService` | Request threads never block on a DB write; a scheduled consumer drains and batches |

## OOP principles

- **Encapsulation**: entities expose behaviour (`VirtualKey#allowsModel`), not just getters/setters, for the rules that belong to them.
- **Abstraction**: controllers and services depend on interfaces (`ProviderAdapter`, `EmbeddingService`) never concrete provider/embedding logic.
- **Inheritance**: `AbstractProviderAdapter` factors out cross-cutting timeout/error-translation behaviour shared by every adapter.
- **Polymorphism**: `ProviderDispatchService` calls `ProviderAdapter.complete(...)`/`streamComplete(...)` without knowing or caring which concrete adapter it's holding.

## DSA / algorithmic choices

- **Rate limiting — lock-free token bucket** (`RateLimiterService`): an `AtomicReference<BucketState>` swapped via a CAS retry loop, continuous refill (no thundering-herd re-admission spike from a hard per-minute reset). See `RateLimiterServiceTest#neverAdmitsMoreThanCapacityUnderConcurrentLoad`.
- **Budget enforcement — atomic conditional UPDATE** (`VirtualKeyRepository#chargeIfWithinBudget`): `UPDATE ... SET spend = spend + :cost WHERE spend + :cost <= budget` in one round trip, so two concurrent requests on the same key can never both be admitted past budget — no optimistic-lock retry storm, no read-modify-write race. See `VirtualKeyRepositoryBudgetTest`.
- **Semantic cache — Locality Sensitive Hashing** (`LshVectorIndex`): random-hyperplane LSH buckets embeddings by the sign pattern of their dot product against K fixed hyperplanes, turning a would-be O(n) linear scan into an O(1)-average bucket lookup, with an exact cosine-similarity check inside the candidate bucket to confirm the match. Scoped per `(tenantKey, model)`.
- **Provider failover — retry with exponential backoff + circuit breaker**: `ProviderDispatchService` retries a failing model with `initialBackoff * multiplier^attempt` delays before moving to the next model in the fallback chain; a per-provider `SimpleCircuitBreaker` (CLOSED/OPEN/HALF_OPEN state machine) skips a known-down provider entirely rather than paying its timeout on every request during an outage.
- **Usage logging — bounded-queue batching**: `UsageLoggingService` uses a `BlockingQueue` + scheduled drain to turn "one INSERT per request" into "one `saveAll` per 200ms tick", amortising transaction overhead; the bound gives back-pressure instead of unbounded memory growth.
- **Smart routing — multi-signal heuristic scoring** (`DifficultyClassifierService`): combines keyword, code/math-notation, multi-step-structure and lexical-density signals with length deliberately down-weighted and capped, specifically so it isn't fooled by the eval set's short-but-hard / long-but-trivial traps the way a length-only baseline is by construction.
- **Alias/model resolution**: O(1) HashMap lookups throughout (`PricingRegistry`, `GatewayRuntimeConfig.aliases()`, `ProviderAdapterFactory`) rather than linear scans, since these run on every single request.

## Caching strategy (multi-level)

1. **L1, in-process (`CaffeineCacheConfig`)** — short-TTL (5s) cache in front of the virtual-key DB lookup, since that runs on literally every request.
2. **L2, semantic response cache (`prism-cache-service`)** — caches whole *responses* for semantically similar prompts, scoped per `(tenantKey, model)`, backed by Postgres with an in-memory LSH index rebuilt at boot and kept live on every write. TTL default 1 hour (`gateway_config.sample.json` → `cache.ttlSeconds`).

## Scalability notes

- The gateway is stateless across requests except for two pieces of process-local state: the rate limiter's token buckets and each provider's circuit-breaker counters. Both are documented here as the reason `prism-gateway-service` should currently be scaled as a *single instance*; the natural next step for horizontal scaling is moving both behind Redis (`INCR`/Lua script for the bucket, a shared key for breaker state) — nothing else in the service holds per-instance state.
- `prism-cache-service` and `prism-usage-service` scale independently of the gateway and of each other.
- Every upstream/peer-service call goes through a WebClient with an explicit connect timeout (provider calls: 2s connect / per-adapter response timeout from config; cache-service calls: 500ms connect / 800ms response, since a cache lookup must fail open fast rather than slow every request down).
- HikariCP pool sizes are tuned per service in `application.yml` with a short connection-timeout so the pool fails fast under saturation instead of queuing indefinitely.
- Database indices: `virtual_keys(key_value)` unique index for the hottest read; `request_logs(virtual_key_id, created_at)` and `usage_records(virtual_key_id, created_at)` composite indices for the "this key's activity in a time range" query pattern that summary/report endpoints run; `cache_entries(tenant_key, model)` for index-rebuild-on-boot and scoped lookups.
- Batch inserts everywhere logs are written (`hibernate.jdbc.batch_size=100`, `order_inserts=true`, plus the queue-based batching described above).

## Validation

Bean Validation (`jakarta.validation`) annotations on every request DTO
(`ChatCompletionRequest`, `ChatMessage`) — non-blank fields, non-empty message lists,
temperature bounds — enforced automatically via `@Valid` and translated to the
`VALIDATION_FAILED` error code by `GlobalExceptionHandler`, so no controller method contains
manual validation code.

## Error handling

Every business-rule rejection is a `PrismException` subtype carrying its own `ErrorCode`
(`prism-common/exception`), caught once by `GlobalExceptionHandler` (`@RestControllerAdvice`)
and translated into the documented error envelope + correct HTTP status — no
controller-level try/catch, and no ad-hoc error shapes anywhere in the codebase.

## Testing

- `RateLimiterServiceTest` — sequential capacity + a genuine 200-thread-concurrent stress test asserting no over-admission.
- `VirtualKeyRepositoryBudgetTest` — 50 concurrent $1 charges against a $10 budget via a real (H2) database, asserting exactly 10 succeed.
- `DifficultyClassifierServiceTest` — the short-but-hard / long-but-trivial traps, plus a trivial-greeting and a code-debugging case.
- `CostServiceTest` — cost computation from the price table.
- `LshVectorIndexTest` — paraphrase hits, unrelated-prompt misses, per-tenant isolation (no cross-tenant leakage), and TTL expiry.

Run everything with `mvn -q test` from the repo root once dependencies are resolvable.

## Known limitations

- **Streaming metadata**: HTTP headers must commit before the first SSE byte, so streaming
  begins with the selected provider, `MISS`, `false`, and a `pending` cost. If a pre-stream
  failure changes the route, or once actual token usage is known, the authoritative values are
  delivered in the `prism-meta` SSE event immediately before `[DONE]`. A production system
  would likely use HTTP trailers for final metadata.
- **Rate limiter and circuit breaker state are process-local** (see "Scalability notes") —
  correct for one gateway instance, needs a Redis-backed implementation to run gateway replicas
  behind a load balancer with a shared, consistent view of these limits.
- **Budget admission is estimate-based**: `BudgetService.reserve()` charges an *estimated* cost
  before the real token count is known (the only way to guarantee no over-admission without
  calling the provider first), then `trueUp()` corrects the ledger once actual usage is known.
  The tiny estimate/actual delta is applied unconditionally rather than re-checked against
  budget, which is logged as a warning if it pushes spend slightly over budget — documented
  rather than hidden.
- **Embeddings are a hashing-trick bag-of-words vector**, not a trained model — good enough to
  catch prompts that share vocabulary (including many real paraphrases) without a network call
  or external dependency, but it will miss paraphrases that reword a prompt with mostly
  different words. `EmbeddingService` is an interface specifically so a real embeddings API can
  be swapped in later without touching `LshVectorIndex` or `SemanticCacheService`.
- **Mock providers only**: `MockProviderAdapter` simulates latency, a configurable transient
  failure rate, and token-by-token streaming, but does not call a real LLM API. Adding one is
  exactly the point of the Adapter pattern here — implement `ProviderAdapter`, register a
  `@Bean`, done.
