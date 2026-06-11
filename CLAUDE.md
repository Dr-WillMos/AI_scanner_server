# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

短视频反诈后端 (Short Video Anti-Fraud Backend) — a Spring Boot 3.5.14 / Java 25 service that accepts short videos, analyzes them via an external Python AI service, calculates risk levels, and manages a blacklist of high-risk publishers. Includes a Vue 3 admin dashboard and Docker deployment support.

## Build & Run Commands

```bash
./mvnw clean compile                          # Compile
./mvnw clean test                             # Run all tests
./mvnw test -Dtest=ClassName                  # Run a single test class
./mvnw clean package                          # Package JAR
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # Start with dev profile

# Admin frontend (aiscanner-admin/)
cd aiscanner-admin && npm install && npm run dev   # Dev server on :5173, proxies /api to :8080
```

Requires: JDK 25, MySQL 8.0+, Redis 6.0+. Initialize DB before first run:
```bash
mysql -u root -p < src/main/resources/sql/init.sql
```
Flyway auto-runs `src/main/resources/db/migration/V1__init.sql` on startup (creates `t_detection_record` and `t_api_key`).

## Architecture

```
     ┌─────────────────────────────────────────────┐
     │  aiscanner-admin (Vue 3 + Element Plus)      │
     │  Port 5173 dev, served static in production   │
     └──────────────┬──────────────────────────────┘
                    │ X-API-Key header
     ┌──────────────▼──────────────────────────────┐
     │  CORS filter (app.cors.allowed-origins)       │
     └──────────────┬──────────────────────────────┘
                    │
     ┌──────────────▼──────────────────────────────┐
     │  ApiKeyFilter @Order(1)                       │
     │  - /actuator/**, POST /api/v1/keys/register → permitAll
     │  - Root key → ROLE_DETECT + ROLE_HISTORY + ROLE_ADMIN, rateLimit=0
     │  - Dynamic keys → validated via Redis cache → MySQL fallback
     │  - Sets request attrs: rateLimit, apiKeyId, apiKeyValue
     └──────────────┬──────────────────────────────┘
                    │
     ┌──────────────▼──────────────────────────────┐
     │  RateLimitFilter @Order(2)                    │
     │  - Fixed-window counter in Redis              │
     │  - Exempt: /actuator/**, admin keys (rateLimit=0)
     │  - Rate-limited → 429 + Retry-After header    │
     └──────────────┬──────────────────────────────┘
                    │
     ┌──────────────▼──────────────────────────────┐
     │  Spring Security URL ACL (hasRole)            │
     │  Stateless, no sessions, CSRF disabled        │
     └──────────────┬──────────────────────────────┘
                    │
         ┌──────────┼──────────┬──────────┬──────────┐
         ▼          ▼          ▼          ▼          ▼
    /detect    /detect/   /history  /blacklist  /dlq
    (sync)     async      /stats    /keys       /health
```

## Detection Flow

### Sync path (`POST /api/v1/detect`)

1. `VideoValidator.validate(video)` — checks non-empty, Content-Type `video/mp4`, `.mp4` suffix, MP4 "ftyp" magic bytes
2. `DetectionService.detect(deviceId, authorId, video)` orchestrates:
   - `BlacklistService.checkBlacklist(authorId)` — checks 3 Redis blacklists (authority > global > temp)
     - Hit → return `DetectResponse.blacklisted()` **no AI call, no DB write**
   - `AiClient.analyze(video)` — POST `/v1/analyze/video` multipart to Python AI service, up to 3 retries (1s/2s/4s backoff). 4xx → immediate fail, 5xx/connection → retry
   - `RiskCalculator.calculate(aiGlitchProb, violenceProb, keywordHit)` → `Result(riskLevel, score, reason)`
     - `keywordHit` → HIGH (score=1.0). Otherwise `0.7*aiGlitchProb + 0.3*violenceProb`, thresholds: >=0.6 HIGH, >=0.3 MEDIUM, <0.3 SAFE
   - `DetectionRecordMapper.insert(record)` → MySQL `t_detection_record` with full `raw_ai_result` JSON
   - If HIGH → auto-add author to `blacklist:temp:{authorId}` with 24h TTL via `SETEX`
3. Returns `DetectResponse`

### Async path (`POST /api/v1/detect/async`)

1. `VideoValidator.validate(video)`
2. `VideoStorageService.save(taskId, video)` → `{storageDir}/{taskId}.mp4`
3. `DetectTaskService.createTask(taskId, deviceId, authorId)` → Redis key `detect:task:{taskId}` (24h TTL)
4. `DetectStreamProducer.send(taskId, deviceId, authorId, filePath)` → Redis Stream `detect:stream`
5. Returns 202 with `TaskSubmitResponse(taskId, PENDING, createdAt)`

**Consumer** (`DetectStreamConsumer` implements `StreamListener`):
- Consumer group `detect-consumers`, consumer `consumer-1`, poll timeout 2s
- `onMessage()` → update status to PROCESSING → `VideoStorageService.read()` → `DetectionService.detect()` → `markDone()` → `delete()` temp file
- On failure: up to 3 retries via `producer.requeue()`; after 3 failures → `producer.sendToDlq()` → stream `detect:stream:dlq`

**Status query** (`GET /api/v1/detect/{taskId}/status`): reads `detect:task:{taskId}` from Redis.

## DLQ (Dead Letter Queue) System

- **DlqRetryScheduler** — `@Scheduled(fixedDelay=30s)`, scans `detect:stream:dlq`, exponential backoff (60s/300s/900s/1800s), max 4 DLQ retries then final-dead
- **DlqService** — list/retry/delete/purge messages with stats (pending count, final dead letters, oldest message time)
- **DlqAlertService** — `@Scheduled(fixedRate=5min)`, warns when pending >= 10 messages, 30min cooldown
- **DlqController** — `GET/POST/DELETE /api/v1/dlq/**`, requires ROLE_ADMIN
- **DlqMessage** DTO — `messageId, taskId, deviceId, authorId, filePath, error, retryCount, dlqRetryCount, enteredAt`

## API Key Management

- **ApiKey entity** — `id, keyValue (32-char UUID), keyName, deviceId, permissions (comma-separated), status (ACTIVE/REVOKED), rateLimit, lastUsedAt, expiredAt, createdAt, revokedAt`
- **ApiKeyService**:
  - `registerKey(deviceId, deviceName)` — auto-registers on first use, returns existing key if already registered. Default permissions: `DETECT,HISTORY`, rateLimit=20
  - `validateKey(keyValue)` — Redis cache first (prefix `apikey:`, TTL 5min), MySQL fallback. Checks status==ACTIVE and not expired
  - Admin CRUD: `createKey`, `listAll`, `getById`, `updateKey`, `revokeKey` (reversible), `deleteKey` (permanent). All invalidate Redis cache
  - `recordUsage(id)` — updates `last_used_at`
- **ApiKeyController**:
  - `POST /api/v1/keys/register` — **permitAll** (device self-registration)
  - `GET/POST/PUT/DELETE /api/v1/keys/**` — ROLE_ADMIN
- **KeyInfo** DTO — `forList()` omits `keyValue`, `full()` includes all fields
- **ApiKeyMapper** — MyBatis mapper, XML at `src/main/resources/mapper/ApiKeyMapper.xml`

## Rate Limiting

- **RateLimitFilter** — `@Order(2)`, fixed-window counter via Redis `INCR` with window-aligned keys (`ratelimit:{keyValue}:{windowStart}`)
- **RateLimitService** — `checkRate(key, limit, windowSeconds)` → `RateLimitResult(allowed, remaining, resetTimeSeconds, limit)`. Graceful degradation: allows all if Redis down
- **RateLimitProperties** — `@ConfigurationProperties("rate-limit")`, defaults: enabled=true, window=60s, defaultLimit=20
- Admin keys (`rateLimit=0`) and `/actuator/**` are exempt
- Sets response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`

## Security (URL Permissions)

`SecurityConfig` defines role-based access:
| URL Pattern | Method | Required Role |
|---|---|---|
| `/actuator/**` | * | permitAll |
| `/api/v1/detect`, `/api/v1/detect/async` | POST | ROLE_DETECT |
| `/api/v1/detect/*/status` | GET | ROLE_DETECT |
| `/api/v1/history` | GET | ROLE_HISTORY |
| `/api/v1/blacklist/**` | * | ROLE_ADMIN |
| `/api/v1/keys/register` | POST | permitAll |
| `/api/v1/keys/**` | * | ROLE_ADMIN |
| `/api/v1/stats` | GET | ROLE_ADMIN |
| `/api/v1/dlq/**` | * | ROLE_ADMIN |

CORS configured from `app.cors.allowed-origins` (default `http://localhost:5173`), exposes rate-limit headers.

## Other Services

- **BlacklistService** — 3 Redis sets/keys: `blacklist:authority` (Set, permanent), `blacklist:global` (Set, permanent), `blacklist:temp:{authorId}` (String, 24h TTL). Check order: authority > global > temp. Temp enumeration via SCAN (max 1000 keys). Graceful degradation on Redis failure.
- **HistoryService** — cursor-based (`afterId`) or offset-based (`page`/`size`) pagination. Supports filters: `authorId`, `riskLevel`, `startDate`, `endDate`. Size clamped 1-100 (default 20).
- **StatsService** — reads from Micrometer `MeterRegistry` (counters/timers) and blacklist counts.
- **DetectionMetrics** — Micrometer counters: `aiscanner.blacklist.hit`, `aiscanner.rate.limit.exceeded`, `aiscanner.detection.count` (tagged `riskLevel`). Timer: `aiscanner.ai.call.duration`.
- **VideoValidator** — validates non-empty, `video/mp4` Content-Type, `.mp4` suffix, MP4 "ftyp" file header signature. Throws `IllegalArgumentException` on failure.
- **VideoStorageService** — saves/reads/deletes video files in `detect.video-storage-dir` (default `/tmp/aiscanner/videos`, Docker `/data/videos`).
- **AIHealthIndicator** — Actuator `HealthIndicator`, GETs AI service root URL. Any HTTP response (even 404) = UP. Connection failure only = DOWN. Exposed at `/actuator/health` (no auth).

## Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps:
| Exception | HTTP code |
|---|---|
| `MaxUploadSizeExceededException` | 400 |
| `IllegalArgumentException` | 400 |
| `MissingServletRequestParameterException` | 400 |
| `MultipartException` | 400 |
| `AiServiceException` | 502 |
| `DataAccessException` | 503 |
| `Exception` (catch-all) | 500 (logs stack trace) |

## Configuration

- `application.yml` — main config with env-var placeholders (`DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `AI_SERVICE_URL`, `SERVER_PORT`, `API_KEY`, `CORS_ORIGINS`)
- `application-dev.yml` — dev overrides (plaintext passwords, DEBUG logging, extended AI timeouts 120s)
- `application-docker.yml` — Docker overrides (mysql→`mysql`, redis→`redis`, AI service→`host.docker.internal:8001`, video dir→`/data/videos`)
- MyBatis: `map-underscore-to-camel-case: true`, mapper XML in `classpath:mapper/*.xml`
- Multipart: max file size 10MB
- HikariCP pool: min-idle 5, max 20
- Flyway: `baseline-on-migrate: true`, migration at `src/main/resources/db/migration/V1__init.sql`
- `api.key` — root/admin API key (env `API_KEY`, default `changeme`)
- `detect.video-storage-dir` — temp video storage for async detection
- `rate-limit.*` — rate limiting config (enabled, window-seconds=60, default-limit=20)
- `ai.service.*` — AI service URL, connect timeout (5s), read timeout (30s)
- `management.endpoints.web.exposure.include: health,info,metrics,prometheus`
- RocketMQ dependency in `pom.xml` is **commented out** — do not uncomment unless explicitly asked

## Admin Frontend (aiscanner-admin/)

Tech stack: **Vue 3** (Composition API) + **TypeScript** + **Vite 6** + **Pinia** + **Vue Router** (hash mode) + **Element Plus** + **ECharts 5** + **Axios**.

- `api/client.ts` — Axios instance with `X-API-Key` header injection, 401 → redirect to login, 429 → rate-limit warning
- `stores/auth.ts` — Pinia auth store (apiKey persisted to localStorage), login validates via `GET /api/v1/stats`
- **Views**: Dashboard (stats cards + charts), Detections (filterable table), Blacklist (3 tabs), Keys (CRUD + revoke), DLQ (stats + retry/delete/purge), Health (3 status cards, 15s auto-refresh)
- Dev server port 5173, proxies `/api` and `/actuator` to `localhost:8080`

## Docker

- **Dockerfile** — multi-stage: `maven:3.9-eclipse-temurin-25` build, `eclipse-temurin:25-jre-alpine` run with non-root `appuser`
- **docker-compose.yml** — 3 services: mysql (8.0, volume `mysql_data`), redis (7-alpine, AOF persistence), app (builds from project, `docker` profile). Network: `aiscanner-net` (bridge). App uses health-check dependency on mysql/redis.
- `.env.example` — documents env vars: `DB_PASSWORD`, `REDIS_HOST/PORT/PASSWORD`, `AI_SERVICE_URL`, `API_KEY`, `SERVER_PORT`

## Testing

### Unit Tests
- **JUnit 5 + Mockito** — `@ExtendWith(MockitoExtension.class)` for service-layer tests
- `ApiKeyFilter` test uses reflection to set `rootKey` field (avoids Spring context)

### Web Slice Tests (`@WebMvcTest`)
- Must explicitly `@Import({SecurityConfig.class, ApiKeyFilter.class})`
- Convention: use `"test-api-key"` as the API key value in test assertions

### Full Integration Tests (`@SpringBootTest`)
- `@AutoConfigureMockMvc` + `@MockitoBean` for infrastructure beans
- Verifies full filter chain + controller interaction

## API Response Convention

All endpoints return `ApiResponse<T>` (`@JsonInclude(NON_NULL)`): `{"code":200, "message":"success", "data":...}`. Factory methods: `ok(data)`, `ok()`, `ok(code, message, data)`, `error(code, message)`.

## Database

Two tables managed by Flyway `V1__init.sql`:
- `t_detection_record` — `id BIGINT AUTO_INCREMENT`, `device_id`, `author_id`, `risk_level VARCHAR(16)`, `score DOUBLE`, `raw_ai_result TEXT`, `created_at`. Indexes on `device_id`, `author_id`, `risk_level`, `created_at`.
- `t_api_key` — `id BIGINT AUTO_INCREMENT`, `key_value VARCHAR(64) UNIQUE`, `key_name`, `device_id`, `permissions` (default `DETECT,HISTORY`), `status` (default `ACTIVE`), `rate_limit` (default 20), `last_used_at`, `expired_at`, `created_at`, `revoked_at`. Index on `device_id`.
