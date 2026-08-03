# Architecture Decision Records (ADR) — Index

> Status: Living · Each ADR: Context / Decision / Consequences / Status. Change decisions by superseding ADRs, not editing history.

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | Backend = Spring Boot (Java 21) modular monolith | Accepted |
| ADR-002 | PostgreSQL as single source of truth; Flyway migrations | Accepted |
| ADR-003 | Redis for caching, booking holds, rate limiting, locks | Accepted |
| ADR-004 | Payments behind provider-agnostic abstraction | Accepted |
| ADR-005 | Object storage for media (metadata/URLs in DB) | Accepted |
| ADR-006 | Auth = JWT access + rotating refresh token | Accepted |
| ADR-007 | Web = Next.js App Router with intentional server/client split | Accepted |
| ADR-008 | Double-booking prevention = tx + DB constraint + Redis hold | Accepted |
| ADR-009 | Maps + media + email providers selected by config (defaults proposed) | Proposed |
| ADR-010 | Redis-backed rate limiting lives in `common/ratelimit`, not per-module | Accepted |

---

## ADR-001 — Spring Boot modular monolith
**Context:** Need production-grade backend serving web + future mobile; team standard; avoid premature microservices. **Decision:** Single Spring Boot (Java 21) app with enforced module boundaries. Do NOT run Spring Boot and NestJS simultaneously. **Consequences:** Simple ops, fast iteration; must enforce boundaries in code review; extraction path preserved.

## ADR-004 — Payment abstraction
**Context:** Multiple providers (Stripe/Paystack/Flutterwave) depending on market; secrets must stay server-side. **Decision:** Define `PaymentProvider` port (create intent, capture, refund, verify webhook); implement adapters; select via config. **Consequences:** Swap providers without touching booking logic; webhook verification centralized; no secret leaks to frontend.

## ADR-008 — Double-booking prevention
**Context:** Concurrency can create overlapping bookings. **Decision:** Wrap confirmation in a DB transaction; enforce a Postgres exclusion/overlap constraint on (property, date-range) for active bookings; take a short-lived Redis hold at checkout start. **Consequences:** Exactly-one-wins under contention; clear "no longer available" error path; requires careful hold-expiry handling.

## ADR-010 — Rate limiter placement
**Context:** `security/01-security-plan.md` requires Redis-backed rate limiting on auth, search, booking, and messaging — i.e. it's cross-cutting, not auth-specific. Prompt 09 (authentication)'s "files you MAY modify" list names `auth/`, `users/`, `security/config` but not `common/`. **Decision:** Implement the limiter once in `common/ratelimit` (a fixed-window Redis `INCR`+conditional-`EXPIRE` counter) and have `auth/` be its first caller, rather than duplicating the logic inside `auth/` and re-duplicating it again in every later module that needs it (ADR-003 already accepts Redis for exactly this). **Consequences:** One tested implementation reused by prompts 11/12/16; a small, deliberate, documented exception to prompt 09's file scope instead of an undocumented one.
