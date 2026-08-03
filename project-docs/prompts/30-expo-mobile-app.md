# Prompt 30 — Expo Mobile App

> Phase: 14 · Order: 30 · Depends on: 23, 29
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Build the Expo/React Native app consuming the SAME Spring Boot API — no second backend, no duplicated business logic — starting with core traveler flows.

## 2. Prerequisite documents
- `../mobile/01-mobile-architecture.md`
- `../architecture/03-api-design.md`
- `../design/02-design-tokens.md`

## 3. Deliverables (exact)
- Expo app (TS, Expo Router, TanStack Query, RHF+Zod) sharing types/schemas with web where practical.
- Core flows: auth (token in secure storage), search, property detail, booking + payment, trips, messaging.
- Brand parity via shared design tokens; push-notification wiring.

## 4. Constraints
- Consume the existing API only — do NOT build a new backend or duplicate business logic.
- All authoritative logic stays server-side.
- Auth works without browser cookies (bearer refresh + secure storage).

## 5. Acceptance criteria
- A user completes search→book→pay→confirmation natively against the same API.
- No business-critical calculation performed on the device.

## 6. Files you MAY modify
- mobile app source
- shared schema package
- `../mobile/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- Backend business logic (consume only)

## 8. Tests required
- Component tests; API integration (mocked); E2E on a device/simulator for core flow.

## 9. Documentation updates required
- Update `../mobile/01-mobile-architecture.md` with parity status.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
