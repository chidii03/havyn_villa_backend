# CLAUDE.md — Havyn Villa

> Persistent project memory for Claude Code. Auto-loaded every session. Read this + `AGENTS.md` before any task. Brand and naming are DONE — never re-open them.

## Product
**Havyn Villa** — a production-grade PropTech marketplace (short-lets, apartments, villas, vacation stays). Travelers discover, book, and pay for quality stays; hosts list, manage, and earn; admins moderate and operate. **API-first:** the Next.js web app now and an Expo app later share ONE Spring Boot backend.

**Tagline:** *Stay beautiful, live better.*
**Positioning:** premium, trust-first, honest (whole prices, real availability). Original brand — study competitor UX for patterns only; never copy markup, CSS, copy, icons, or assets.

## Roles
Guest (browse, no auth) · Customer/Traveler · Host · Admin. RBAC enforced server-side.

## Fixed stack
- **Backend:** Java 21, Spring Boot (Web, Data JPA/Hibernate, Security, Validation, Actuator), springdoc OpenAPI, Flyway, PostgreSQL, Redis. **No Prisma. No second backend.**
- **Web:** Next.js (App Router) + TypeScript + Tailwind + shadcn/ui + TanStack Query + React Hook Form + Zod + Framer Motion + Lucide. Intentional server/client split.
- **Mobile (future):** Expo + React Native + Expo Router — same API.
- **Infra:** Docker Compose (local), CI/CD, object storage + CDN, secrets manager.

## Integrations (decided — see project-docs/architecture/04-integrations.md)
- **Media = Cloudinary** (images + video). DB stores **URL + metadata only — never binaries, never full video.** Server-signed direct upload; API secret server-side only; deliver via Cloudinary CDN transformations; store `secure_url, public_id, resource_type, format, width, height, duration, bytes, position, alt`.
- **Maps = Google Maps.** Browser key is **referrer-restricted** (`NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY`); geocoding runs server-side with `GOOGLE_MAPS_SERVER_KEY`. Places Autocomplete powers the "Where" search; results map has clustered price-pin markers; detail shows approximate area until booked.
- **Payments** provider-agnostic (Paystack/Flutterwave for NG/Africa; Stripe elsewhere) behind a `PaymentProvider` port; secrets server-side; webhooks signature-verified + idempotent.
- **Email** behind a `Mailer` port (Mailhog local; transactional provider in prod).

## Canonical rules (non-negotiable)
1. **Backend is the authoritative business layer.** Pricing/totals/availability computed and **verified server-side**; never trust client totals. Prevent double-booking with DB transaction + Postgres exclusion constraint + Redis hold.
2. PostgreSQL = source of truth; Redis = cache/holds/locks/rate-limit only.
3. No hardcoded secrets · no fake production data · no frontend-only functionality · no duplicated logic web↔mobile.
4. Modular monolith with clean module boundaries (future extraction possible).
5. Every task ships tests (JUnit + Testcontainers backend; React Testing Library + axe frontend), doc updates, and verification (build + tests + health checks) before "done".
6. WCAG 2.2 AA and OWASP practices are floors. Brand blue `#0039A6` (Boeing Blue) for all active/selected/focus states (light mode; dark mode keeps its own lighter blue for contrast — see `brand/brand-colors.md`).

## Brand (frontend)
Blue system from the logo: `brand #0039A6 · brand-strong #002874 · sky #38BDF8 · sun #F4B740 · ink #0F1B2D · ink-muted #5B6B7F · bg #F5F8FC · surface #FFFFFF · line #E1E8F0`. Display/wordmark: warm serif (Fraunces/Playfair); UI: Inter (tabular figures for prices). Assets: `brand/logo.png`, `brand/favicon.png`, tokens `brand/brand-colors.md`. Avoid generic-AI dashboards, excessive gradients, glassmorphism, template looks — photography-led, calm motion.

## UI & navigation (full spec: project-docs/frontend/03-ui-and-navigation-spec.md)
- **Header** (sticky, condenses on scroll): logo · center tabs `Homes · Experiences · Services` (**active tab animates to brand blue**) · right `Become a host` + locale + ProfileMenu.
- **Search** pill = **Where / When / Who / Search**; each segment opens a popover (destination typeahead via Google Places, date-range calendar, guest steppers); expanded on Home, compact pill on scroll; mobile = full-screen sheet; routes to `/search?...` (URL-synced).
- **Explore**: scrollable **CategoryChips** (selected = brand blue) + Filters sheet + themed **carousels** of PropertyCards (each card has an image carousel + wishlist heart).
- **Footer**: Support / Hosting / Havyn Villa columns + bottom bar (Privacy, Terms, locale + currency, socials) + tagline.
- **Pages:** public `/`, `/search` (grid + Google map), `/rooms/[id]` (gallery+lightbox, amenities, reviews, map, sticky booking widget), `/experiences`, `/services`, `/become-a-host`, `/login`, `/signup`. Auth: `/wishlists`, `/trips`, `/messages`, `/account`. `/host`, `/admin`. **`Trips` and `Messages` appear in nav only when signed in.**

## Repository layout (monorepo)
```
havyn-villa/
├─ CLAUDE.md · AGENTS.md · README.md
├─ project-docs/            # source of truth (docs + prompts/)
├─ brand/                   # logo.png, favicon.png, brand-colors.md
├─ apps/
│  ├─ api/                  # Spring Boot (Java 21) modular monolith
│  └─ web/                  # Next.js App Router
├─ packages/shared/         # shared TS types + Zod schemas
├─ infra/docker-compose.yml # postgres, redis, minio, mailhog
└─ .github/workflows/       # CI
```

## Workflow
Build documentation-driven, one prompt per session, from `project-docs/prompts/` (start at `00` then `08`). Read each prompt + its prerequisites first; obey its may/must-not lists; write tests + update docs; run build/tests; end with a concise summary of what changed, what's tested, and what's next. Don't start the next phase until the current one builds and its tests pass. See `AGENTS.md` for the exact session plan.
