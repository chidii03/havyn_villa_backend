# Phased Build Roadmap — Havyn

> Status: Draft for approval · Owner: CTO/PM. Documentation-driven: each phase has prerequisite docs, a matching implementation prompt in `../prompts/`, and exit criteria. Do not begin a phase until prior docs are approved.
>
> **See `02-launch-checklist.md` for the prompt 31 launch sign-off: NO-GO for
> public launch as of session 20.** Feature-complete on every core flow across
> web, mobile, host, and admin — what remains is almost entirely legal/business/
> infra provisioning (trademark, domain, Paystack account, ToS/Privacy Policy,
> app store accounts, on-call) this engagement cannot perform, plus two small,
> bounded code fixes (a PII leak, a missing kill switch). Read that document
> before treating Phase 14 as closed.

## Principles
Modular monolith first; backend is authoritative; tests + docs each phase; no fake data, no hardcoded secrets, no frontend-only functionality. Phases build on one another.

| Phase | Name | Primary prompt(s) | Key docs | Exit criteria |
|---|---|---|---|---|
| **0** | Discovery + Brand | 01, 02 | discovery/*, brand/* | Brand winner approved (pending legal), discovery signed off |
| **1** | Product requirements + UX | 03, 04, 05 | product/*, design/* | PRD, stories, design tokens approved |
| **2** | Architecture + Database | 06, 07 | architecture/*, database/* | Architecture + ERD + ADRs approved |
| **3** | Backend foundation | 08, 09 | backend/*, security/* | App boots, auth works, migrations, Actuator, Swagger, tests green |
| **4** | Frontend foundation | 19 | frontend/* | Next.js app, theming, auth, API client, protected routes |
| **5** | Property marketplace | 10, 11, 20 | properties/search/media | Listings + search/filter + detail + gallery live end-to-end |
| **6** | Booking engine | 12, 21 | booking/pricing | Server-verified pricing + no-double-booking + booking UI |
| **7** | Payments | 13, 14 | payments/media-storage | Provider-agnostic pay + webhook verify + media storage |
| **8** | Host platform | 17 | host dashboard | Host listing mgmt, calendar, reservations, earnings |
| **9** | Messaging + notifications | 16, 15 | messaging/reviews/favorites | Conversations, notifications, reviews, favorites |
| **10** | Admin | 18 | admin | Moderation, disputes, KYC, settings, analytics, commissions |
| **11** | Testing + security | 23, 24 | testing/*, security/* | Coverage gates, OWASP hardening, pen-test checklist |
| **12** | Performance + observability | 25, 26 | devops/observability | Caching, load targets, logs/metrics/traces, dashboards/alerts |
| **13** | Deployment | 27, 28, 29 | devops/* | Dockerized, CI/CD, staging→prod, production-readiness review |
| **14** | Expo mobile app | 30, 31 | mobile/* | Native app on same API; launch checklist complete |

## Sequencing rules
- Do not implement future categories (hotels/long-stay/experiences), AI search, or geospatial radius during MVP — architecture only.
- Payments and booking must land before host earnings/payouts.
- Security/testing are continuous but get a dedicated hardening phase (11).
- Mobile (14) only after the API is stable and contract-tested.

## Risk checkpoints
End of Phase 2 (data model + concurrency design review), Phase 6 (double-booking + pricing verification), Phase 7 (payment security review), Phase 13 (production-readiness), Phase 14 (mobile parity).
"""