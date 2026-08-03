# Mobile Architecture — Expo

> Status: ✅ Core traveler flows implemented · `prompts/30-expo-mobile-app.md`, session 20 · Owner: Mobile Architect. Host tools still NOT built — see Parity status below.

## Stack
Expo SDK 57, React Native 0.86, TypeScript, Expo Router, TanStack Query, React Hook Form + Zod, NativeWind (Tailwind for React Native), `expo-secure-store`.

## Key principle
The mobile app consumes the **same Spring Boot API** — no second backend, no duplicated business logic. All authoritative logic (pricing, booking, payments) stays server-side.

## What MVP must guarantee for mobile-readiness
- Clean, versioned REST API with OpenAPI (client generation possible).
- Auth that works without browser cookies: access token + refresh token usable from secure device storage (SecureStore/Keychain) — API accepts bearer refresh as well as cookie.
- Payments flow that supports mobile SDKs/redirects via the same provider abstraction.
- Media served via URLs/CDN (already the case).
- Push-notification-capable notification service design (channel-agnostic).
- Shared Zod schemas/types publishable to a shared package for web+mobile.

## Parity plan
Reuse design tokens (`design/02-design-tokens.md`) for a consistent brand on native. Feature parity targets core traveler flows first (search, detail, book, trips, messaging); host tools follow.

## Parity status (session 20)

App lives at `apps/mobile`, a new npm workspace consuming the same
`GET/POST /api/v1/*` endpoints as `apps/web` — **zero backend changes were needed**,
confirming the "MVP must guarantee" bullets above actually held: `AuthController#refresh`
already accepted a refresh token in the request body (the web client's own `refresh()`
comment says as much — "refreshToken is only needed for a mobile client"), so bearer-only
auth (no cookies) worked against the existing API unmodified.

| Flow | Status | Notes |
| --- | --- | --- |
| Auth (login/signup/logout/silent refresh) | ✅ Real | `expo-secure-store` (Keychain/Keystore) holds the refresh token; access token kept in memory only. Bearer `Authorization` header, no cookies. |
| Search / browse | ✅ Real, simplified | Explore tab lists `GET /properties` with no filter UI yet (apps/web has query/filter params mobile doesn't expose) — acceptable first pass, not a fake list. |
| Property detail | ✅ Real | Real amenities/pricing from `GET /properties/{id}`; native `DateTimePicker` instead of a calendar-grid UI (a deliberate mobile-first simplification, not a stub). |
| Book (quote + reserve) | ✅ Real, stops at the same honest boundary as web | Creates a real server-verified `PENDING` hold via `POST /bookings`. Stops there — **no live Paystack account exists anywhere this project has run** (same gap `booking-widget.tsx` documents on web); mobile does not fake a "confirmed" state past that. |
| Payment / confirmation | ⛔ Not built (matches web) | Deferred until a real Paystack integration exists for either platform — tracked as its own future prompt, not a mobile-specific gap. |
| Trips (view/cancel) | ✅ Real | `GET/DELETE /bookings` via the same endpoints web's `/trips` page (once built) would use. |
| Messaging | ✅ Real — first frontend consumer of this backend | The messaging backend (session 17) had **no frontend consumer at all** until this session — `apps/web`'s own `/messages` page is still an `EmptyState` stub. Mobile's `app/(tabs)/messages.tsx` + `app/conversation/[id].tsx` are real, working screens against the real `GET/POST /conversations` endpoints; this is legitimate prompt-30 scope ("messaging" is named explicitly), not scope creep into web's gap. |
| Push notifications | ⛔ Not wired | Prompt named this as a deliverable; not implemented this session — needs an Expo push token registration endpoint on the backend (none exists) and is sized as its own prompt. |
| Host tools | ⛔ Not built | Explicitly out of scope per this doc's own parity plan above ("host tools follow"). |

**Known gap surfaced, not fixed**: `app/login.tsx` always redirects to `/(tabs)/account`
after login, regardless of what triggered the login screen (e.g. reserving while signed
out) — there's no return-to-intent redirect. Web has the same gap. Documented in
`apps/mobile/e2e/README.md`; worth its own small prompt (`?redirect=` param, mirrored on
both apps) rather than a silent fix bundled here.

**Testing**: component tests + mocked API integration tests (Jest + `jest-expo` +
`@testing-library/react-native`, 16 tests across 4 suites, all passing) cover
`PropertyCard`, `TripsScreen`, the HTTP client, and `AuthProvider`'s full auth
lifecycle. A real Maestro E2E flow (`apps/mobile/e2e/core-flow.yaml`) covers
search → detail → reserve end-to-end against a live app + API, stopping at the same
`PENDING`/"Dates held" boundary as `apps/web/e2e/search-and-reserve.spec.ts` — **not
executed in this dev sandbox** (no simulator/emulator/device available here; see that
file's own README for the exact gap), the same category of limitation already
documented for every Testcontainers/Playwright suite in this project.
