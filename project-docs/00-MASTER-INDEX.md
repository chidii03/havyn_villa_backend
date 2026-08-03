# Havyn Villa — Master Index

> **Product:** Havyn Villa — premium PropTech marketplace (short-lets, apartments, villas, vacation stays).
> **Tagline:** *Stay beautiful, live better.*
> **Status:** Discovery + brand are DONE. This bundle is **build-ready** — documentation-driven implementation via `prompts/`.
> **Golden rule:** backend is the authoritative business layer; follow the prompts in order; tests + docs + verification every task.

Brand identity and naming are finalized and intentionally **not** re-litigated here — the final kit (logo, favicon, blue color system, fonts) lives in `../brand/`. Agent build instructions live in `../CLAUDE.md` and `../AGENTS.md`.

---

## How to use this repo
1. Read `../CLAUDE.md` and `../AGENTS.md` (loaded automatically by the coding agent).
2. Execute `prompts/` in order — one phase per session — obeying each prompt's "may/must-not modify" lists.
3. Every task: write tests, update docs, run build + tests + health checks before completion.

---

## Document map

### product/
- `01-product-requirements.md` — MVP PRD (MoSCoW)
- `02-user-stories-and-acceptance.md` — stories + acceptance criteria
- `03-business-model.md` — revenue (host commission + optional guest fee)

### design/
- `01-design-principles.md` · `02-design-tokens.md` (blue system) · `03-ux-flows.md` · `04-accessibility.md`

### architecture/
- `01-system-architecture.md` — modular monolith, API-first
- `02-adr-index.md` — decision records
- `03-api-design.md` — REST conventions + endpoints
- `04-integrations.md` — **Cloudinary (media, URL-only)**, **Google Maps**, payments, email

### database/
- `01-data-model.md` — entities + ERD + concurrency · `02-migrations-and-conventions.md` (Flyway, no Prisma)

### backend/
- `01-backend-foundation.md` · `02-domain-modules.md`

### frontend/
- `01-frontend-foundation.md` — Next.js App Router, server/client split
- `02-components-and-patterns.md` — component inventory
- `03-ui-and-navigation-spec.md` — **app shell, nav tabs (active = brand blue), where/when/who search, carousels, footer, pages (Explore/Wishlists/Trips/Messages/Profile), maps + media UI**

### mobile/
- `01-mobile-architecture.md` — Expo readiness (future)

### security/ · testing/ · devops/ · roadmap/
- `security/01-security-plan.md` · `testing/01-testing-strategy.md`
- `devops/01-devops-and-deployment.md` · `devops/02-observability.md`
- `roadmap/01-phased-roadmap.md` — Phases 0–14 with exit criteria

### discovery/ (context, read-only)
- problem, market, personas/JTBD (kept for reference; discovery is complete).

### prompts/
- `00-project-context.md` … `31-launch-checklist.md` — optimized, ordered implementation library (`02-brand-identity` removed; brand is done). See `prompts/README.md`.

---

## Canonical constraints (apply everywhere)
- Backend = **Spring Boot (Java 21)** modular monolith; **no Prisma**; one backend for web + future mobile.
- PostgreSQL = source of truth; Redis for cache/holds/locks/rate-limit.
- **Media = Cloudinary**, DB stores **URL/metadata only — never binaries, never full video**.
- **Maps = Google Maps** (referrer-restricted browser key; geocoding server-side).
- Payments provider-agnostic (Paystack/Flutterwave/Stripe), secrets server-side, webhooks verified.
- Backend authoritative — **no business-critical math on the frontend**; server verifies every booking total; prevent double-booking (tx + exclusion constraint + Redis hold).
- No fake production data · no hardcoded secrets · no frontend-only functionality.
- Brand blue `#0B5FD0` for all active/selected/focus states. WCAG 2.2 AA · OWASP.
- Study competitor UX **patterns** only — design, assets, copy, and icons are original Havyn Villa.
