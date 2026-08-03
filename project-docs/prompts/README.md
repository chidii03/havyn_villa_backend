# Implementation Prompt Library (optimized)

> Ordered, dependency-aware prompts for building Havyn Villa autonomously. Brand + naming are DONE, so `02-brand-identity` has been removed. Execute in order; each builds on the last.

## Autonomous execution contract
For **every** prompt: (1) read the prompt file and its prerequisite docs first; (2) implement **only** its scope; (3) obey the "may / must-not modify" lists; (4) write the required tests; (5) update the named docs; (6) run build + tests + health checks and self-review against the acceptance criteria before marking done. If a needed decision isn't in the docs, add a short ADR in `../architecture/` and proceed with the documented default; only stop for genuinely blocking questions.

Each prompt keeps the 10-part contract: Objective · Prerequisites · Deliverables · Constraints · Acceptance · MAY modify · MUST-NOT modify · Tests · Docs · Verification.

## Global build facts (apply to all prompts)
- Backend authoritative (Spring Boot, Java 21); **no Prisma**; Postgres source of truth; Redis for cache/holds/locks/rate-limit.
- **Cloudinary** media (images + video) — DB stores **URL/metadata only, never binaries/full video**.
- **Google Maps** (referrer-restricted browser key; geocoding server-side).
- Payments provider-agnostic (Paystack/Flutterwave/Stripe); secrets server-side; webhooks verified.
- Brand blue `#0B5FD0` for all active/selected/focus states. WCAG 2.2 AA · OWASP.
- Original design — competitor UX studied for **patterns** only; no copied markup/CSS/copy/icons/assets.

## Order & phases
| # | Prompt | Phase |
|---|---|---|
| 00 | project-context | 0 |
| 01 | discovery (context; complete) | 0 |
| 03 | product-requirements | 1 |
| 04 | user-experience | 1 |
| 05 | design-system | 1 |
| 06 | system-architecture | 2 |
| 07 | database-design | 2 |
| 08 | backend-foundation | 3 |
| 09 | authentication | 3 |
| 10 | property-domain | 5 |
| 11 | search-discovery | 5 |
| 12 | booking-engine | 6 |
| 13 | payments | 7 |
| 14 | media-storage (Cloudinary) | 7 |
| 15 | reviews-favorites | 9 |
| 16 | messaging-notifications | 9 |
| 17 | host-dashboard | 8 |
| 18 | admin-platform | 10 |
| 19 | frontend-foundation & app shell | 4 |
| 20 | property-ui (cards/carousels/maps) | 5 |
| 21 | booking-ui | 6 |
| 22 | responsive-mobile-web | 6 |
| 23 | testing | 11 |
| 24 | security-hardening | 11 |
| 25 | performance | 12 |
| 26 | observability | 12 |
| 27 | docker-deployment | 13 |
| 28 | ci-cd | 13 |
| 29 | production-readiness | 13 |
| 30 | expo-mobile-app | 14 |
| 31 | launch-checklist | 14 |

Note: `02-brand-identity` removed (brand complete). Numbers are canonical reference order; follow the **phase** column and each prompt's "Depends on" for actual sequencing.

## Recommended session batching
1: 08 → 2: 09 → 3: 19 → 4: 10+11 → 5: 20 → 6: 12+21 → 7: 13+14 → 8: 22 → 9: 17 → 10: 15+16 → 11: 18 → 12: 23+24 → 13: 25+26 → 14: 27+28+29 → later: 30+31.
