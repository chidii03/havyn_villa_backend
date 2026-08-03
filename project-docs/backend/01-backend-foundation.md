# Backend Foundation — Spring Boot

> Status: Draft · Phase 3 · Owner: Senior Spring Boot Engineer.

## Stack (fixed)
Java 21+, Spring Boot, Spring Web, Spring Data JPA + Hibernate, Spring Security, Bean Validation, Spring Boot Actuator, OpenAPI/Swagger (springdoc), Flyway, PostgreSQL, Redis. **No Prisma.** Build: Gradle (or Maven) — pick one and document.

## Project layout (package-by-module)
```
com.havyn
 ├─ config/            (security, cors, redis, openapi, jackson)
 ├─ common/            (error envelope, pagination, base entities, events)
 ├─ auth/              (controller, service, jwt, refresh)
 ├─ users/            profiles/  hosts/
 ├─ properties/       media/     amenities/
 ├─ search/
 ├─ booking/          pricing/
 ├─ payments/         (port + adapters: stripe/paystack/flutterwave)
 ├─ reviews/          favorites/
 ├─ messaging/        notifications/
 ├─ admin/            audit/
```
Each module: `web` (controllers/DTOs), `domain` (entities/services), `repo` (JPA). Modules depend on interfaces, not each other's internals.

## Cross-cutting
- **Error handling:** `@ControllerAdvice` → consistent envelope + traceId; no stack traces to clients.
- **Validation:** Bean Validation on DTOs; never trust client input; recompute money server-side.
- **Security:** stateless JWT filter; method-level `@PreAuthorize` for RBAC; see `security/`.
- **Config:** 12-factor env vars; profiles (local/staging/prod); secrets via env/secret manager, never in code.
- **Observability:** Actuator health/readiness/metrics; structured JSON logging with correlation id.
- **API docs:** springdoc served at `/swagger-ui`.
- **Idempotency:** filter/store for booking + payment-intent endpoints.

## Testing baseline
JUnit 5 + Spring Boot Test; Testcontainers for Postgres/Redis; MockMvc/WebTestClient for controllers; contract tests for the API used by web+mobile. See `testing/`.

## Definition of done (backend module)
Migrations added; DTO validation; RBAC enforced; unit + integration tests (incl. Testcontainers); OpenAPI updated; error paths covered; no secrets; docs updated.

## Session 1 / prompt 08 — status and deviations

**Build tool:** Gradle (Groovy DSL), not Maven — chosen per prompt 08's "pick one and
document." Wrapper included (`./gradlew`), Gradle 9.5.1, no local Gradle/JDK-toolchain
install required.

**Versions actually used (verified against Maven Central, not assumed):** Spring Boot
`3.5.3` — the latest real GA release. **Spring Initializr's generated `build.gradle`
initially referenced a nonexistent `4.1.0` with fabricated artifact names**
(`spring-boot-starter-webmvc`, `spring-boot-starter-actuator-test`, etc., and a
`org.testcontainers.postgresql.PostgreSQLContainer` import that doesn't exist — the
real class is `org.testcontainers.containers.PostgreSQLContainer`). All of these
returned zero results on a live Maven Central search and were corrected before any
other code was written. If you regenerate scaffolding from start.spring.io in a
future session, verify the returned coordinates against `search.maven.org` before
trusting them.

**Java:** targets Java 21 via `sourceCompatibility`/`targetCompatibility`, not a pinned
Gradle toolchain — this environment only has JDK 23 installed and no toolchain
auto-provisioning configured, so a pinned `languageVersion(21)` toolchain fails to
resolve. Works on any JDK 21+.

**Security baseline:** `SecurityConfig` is intentionally permissive (`permitAll()` on
every path) — there are no protected endpoints yet. Prompt 09 must replace this with
the real JWT filter + `@PreAuthorize` RBAC (see the `TODO(prompt 09)` comment in the
file). `PasswordEncoder` is BCrypt(12) as a placeholder; the security plan's target is
Argon2id — prompt 09 should swap it.

**What's implemented:** package-by-module skeleton (all 17 domain module packages from
this doc, each with a `package-info.java` describing its future scope); global error
envelope (`common/error`) matching `project-docs/architecture/03-api-design.md`;
`X-Correlation-Id` request filter + MDC-based trace ids in logs and error responses;
pagination envelope (`common/web/PageResponse`); `BaseEntity` + JPA auditing
(`common/persistence`); a `DomainEvent` marker interface (`common/events`); Actuator
health/readiness + OpenAPI/Swagger; CORS allowlist from `WEB_BASE_URL`; a
JSON-serializing `RedisTemplate`; Flyway `V1__init.sql` seeding **reference data only**
(role, property_type, amenity taxonomies — no fake business data).

**Known gap — Testcontainers tests need Docker, which isn't installed in this
environment.** `ApiApplicationTests` (boot/context-load) and `RoleRepositoryIT`
(Postgres + Redis integration) are written and compile, but fail with
`DockerClientProviderStrategy: no Docker daemon` here — verified that's the *only*
failure cause, not a code defect. The Docker-independent suite passes:
`GlobalExceptionHandlerTest` (5/5, a `@WebMvcTest` slice with `SecurityConfig`
imported) exercises the full error-envelope contract without a database. Install
Docker Desktop and run `./gradlew test` to confirm the rest.

### Running locally
```
cd infra && docker compose up -d          # postgres, redis, minio, mailhog
cd apps/api && ./gradlew bootRun          # http://localhost:8080
```
- Health: `GET /actuator/health` · Swagger UI: `/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`
- Tests: `./gradlew test` (Testcontainers tests need Docker running)
- Build only: `./gradlew build -x test`
