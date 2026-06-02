# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

短视频反诈后端 (Short Video Anti-Fraud Backend) — a Spring Boot 3.5.14 / Java 25 service that accepts short videos, analyzes them via an external Python AI service, calculates risk levels, and manages a blacklist of high-risk publishers.

## Build Commands

```bash
./mvnw clean compile          # Compile
./mvnw clean test             # Run all tests
./mvnw test -Dtest=ClassName  # Run a single test class
./mvnw clean package          # Package JAR
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # Start with dev profile
```

Requires: JDK 25, MySQL 8.0+, Redis 6.0+. Initialize DB before first run:
```bash
mysql -u root -p < src/main/resources/sql/init.sql
```

## Architecture

```
Client (mobile app)
    │
    ▼  X-API-Key header required (except /actuator/**)
┌──────────────────────────────────────┐
│  ApiKeyFilter (Spring Security)       │
│  Stateless, no sessions, CSRF disabled│
└──────────────────────────────────────┘
    │
    ▼
POST /api/v1/detect                    (video upload + detection)
GET /api/v1/history                    (detection history, cursor or offset pagination)
GET/POST/DELETE /api/v1/blacklist/{authority|global|temp}  (blacklist CRUD)
GET /actuator/health                   (no auth — AI service health check)
    │
    ▼
Spring Boot Controller Layer
    │
    ├─► Redis (3 blacklists) — check authorId, short-circuit to HIGH if in any
    ├─► Python AI Service (/v1/analyze/video) — HTTP POST multipart
    └─► MySQL (t_detection_record) via MyBatis — persist results
```

## Detection Flow

1. `DetectionController.detect(deviceId, authorId, video)` receives multipart request
2. `BlacklistService.checkBlacklist(authorId)` → checks all three blacklists in order:
   - `blacklist:authority` Set — official platform blacklist (permanent)
   - `blacklist:global` Set — admin manually added (permanent)
   - `blacklist:temp:{authorId}` String key — auto-added on HIGH risk (24h TTL)
   - Returns `BlacklistHit` record (hit, source, reason)
   - Hit → return `DetectResponse.blacklisted()` with source and reason, **no AI call, no DB write**
3. `AiClient.analyze(video)` → `POST /v1/analyze/video` multipart to Python AI service
   - Uses `aiRestClient` bean (named qualifier) with configurable `ai.service.url` and `ai.service.timeout`
   - Wraps exceptions as `RuntimeException("AI 服务调用失败: ...")`
4. `RiskCalculator.calculate(aiGlitchProb, violenceProb, keywordHit)` returns a `Result` record:
   - `keywordHit == true` → `HIGH (score=1.0, reason="关键词命中")`
   - `score = 0.7 * aiGlitchProb + 0.3 * violenceProb`
     - `>= 0.6` → HIGH, `>= 0.3` → MEDIUM, `< 0.3` → SAFE
5. `DetectionRecordMapper.insert(record)` → MySQL `t_detection_record`
   - `raw_ai_result` column stores full AI response JSON for audit
   - MyBatis `useGeneratedKeys=true` populates `record.id`
6. If result is HIGH, **auto-add author to temp blacklist** with 24h TTL (`SETEX blacklist:temp:{authorId} 86400 <reason>`)
7. Return `DetectResponse` (now includes `source` field on blacklist hits) wrapped in `ApiResponse.ok()`

## Security (API Key Authentication)

Spring Security enforces API key auth on all business endpoints via `ApiKeyFilter`:

- Clients pass `X-API-Key` header on every request
- `/actuator/**` endpoints are **permitAll** (no key needed) — used for health checks and monitoring
- All other endpoints require `ROLE_API_CLIENT` authority, which the filter grants on valid key match
- Session management is **stateless** (`SessionCreationPolicy.STATELESS`), CSRF is disabled
- Invalid/missing key → `401` with JSON body `{"code":401,"message":"Missing or invalid API key"}`
- The API key is configured via `api.key` property (env `API_KEY`, default `changeme`)

## History Endpoint

`GET /api/v1/history?deviceId={id}&afterId={id}&page={n}&size={n}` — query detection history for a device:

- **Cursor-based** (incremental sync): pass `afterId` — fetches records after that ID. Ignores `page`. Returns `size+1` internally to detect `hasMore`.
- **Offset-based** (page browsing): omit `afterId` — uses `page` and `size` for traditional pagination.
- `size` clamped to 1–100 (default 20), `page` minimum 1.
- `HistoryService` calls `DetectionRecordMapper` methods: `countByDeviceId`, `selectByDeviceIdAfterId`, `selectByDeviceIdPaged`.

## Key Service Boundaries

- **BlacklistService** — Manages 3 Redis blacklists via `StringRedisTemplate`:
  - `blacklist:authority` — Set (permanent), from official platform announcements
  - `blacklist:global` — Set (permanent), admin manually added
  - `blacklist:temp:{authorId}` — String keys with 24h TTL (`SETEX`), auto-added when HIGH risk detected
  - `checkBlacklist(authorId)` → `BlacklistHit` record (priority: authority > global > temp)
  - Per-type CRUD: `addToAuthority/Global/Temp`, `removeFromAuthority/Global/Temp`, `listAuthority/Global/Temp`, `isInAuthority/Global/Temp`
  - Temp list uses SCAN for enumeration; `getTempTtl(authorId)` returns remaining seconds
- **DetectionService** — orchestrates: blacklist check → AI call → risk calc → DB insert. Constructor-injected dependencies.
- **AiClient** — `RestClient` HTTP client. Sends `MultipartFile` bytes as `ByteArrayResource`. Exception → RuntimeException.
- **RiskCalculator** — `@Component`, pure function. Returns `record Result(RiskLevel riskLevel, double score, String reason)`.
- **HistoryService** — Queries `t_detection_record` by deviceId. Two strategies: cursor-based incremental sync (`afterId` param) and offset-based page browsing (`page`/`size`). Uses `HistoryItem.from(record)` DTO mapping.
- **AIHealthIndicator** — Spring Boot Actuator `HealthIndicator`. Pings AI service root URL via `aiRestClient` using `exchange()` (treats any HTTP response, even 404, as reachable). Only connection-level failures report `DOWN`. Exposed at `/actuator/health` (no auth required).

## API Response Convention

All endpoints return `ApiResponse<T>` (`@JsonInclude(NON_NULL)`): `{ "code": 200, "message": "success", "data": ... }`. Factory methods: `ApiResponse.ok(data)`, `ApiResponse.ok()` (null data), `ApiResponse.error(code, message)`.

## Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps:
| Exception | HTTP code |
|---|---|
| `MaxUploadSizeExceededException` | 400 |
| `MissingServletRequestParameterException` | 400 |
| `MultipartException` | 400 |
| `IllegalArgumentException` | 400 |
| `Exception` (catch-all) | 500 (logs stack trace, returns generic message) |

## Configuration

- `application.yml` — main config with env-var placeholders (`DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `AI_SERVICE_URL`, `SERVER_PORT`, `API_KEY`)
- `application-dev.yml` — dev overrides with plaintext defaults (api.key=dev-api-key-123), DEBUG logging on `org.example.aiscanner_server`
- MyBatis: `map-underscore-to-camel-case: true`, mapper XML in `classpath:mapper/*.xml`
- Multipart: max file size 10MB
- HikariCP pool: min-idle 5, max 20
- `api.key` — API key for `X-API-Key` header auth (env `API_KEY`, default `changeme`)
- `management.endpoints.web.exposure.include: health,info` — Actuator endpoints exposed without auth
- `ai.service.timeout` — connect and read timeout for AI service HTTP client (default 30s)

## Testing

### Unit Tests

- **JUnit 5 + Mockito** — `@ExtendWith(MockitoExtension.class)` for service-layer tests
- Mock dependencies with `@Mock`, inject real `RiskCalculator` (pure function, trivial to construct)
- `ApiKeyFilter` test uses reflection to set `apiKey` field (avoids Spring context for speed)

### Web Slice Tests (`@WebMvcTest`)

- Test a single controller with mocked beans via `@MockitoBean`
- **Must explicitly `@Import({SecurityConfig.class, ApiKeyFilter.class})`** — Spring Security is not auto-configured in `@WebMvcTest` slices
- The convention is to use `"test-api-key"` as the API key value in test assertions

### Full Integration Tests (`@SpringBootTest`)

- Use `@AutoConfigureMockMvc` + `@MockitoBean` to mock infrastructure beans (`RedisConnectionFactory`, services) so Redis/MySQL aren't needed
- Verify the full filter chain + controller interaction

## Integration Test (Python)

`test_aiscanner.py` — Python script that starts a Flask mock AI service (port 8000), seeds Redis with 5 blacklist entries, then runs end-to-end tests: blacklist CRUD, blacklist short-circuit, keyword hit, scoring boundary values, missing parameter errors. Run after starting the Spring Boot app with dev profile.

## RocketMQ

Dependency is in `pom.xml` but **commented out**. Config is in `application.yml` but **commented out**. Do not uncomment unless explicitly asked.
