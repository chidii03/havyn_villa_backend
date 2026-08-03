# Prompt 16 — Messaging & Notifications

> Phase: 9 · Order: 16 · Depends on: 12
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement guest↔host messaging (conversations per property/booking) and a channel-agnostic notification service (in-app + email now; realtime later).

## 2. Prerequisite documents
- `../architecture/01-system-architecture.md`
- `../backend/02-domain-modules.md`
- `../design/03-ux-flows.md`

## 3. Deliverables (exact)
- Conversation + Message entities/endpoints tied to property/booking; authz to participants only.
- Notification service (in-app persistence + email) subscribing to domain events (booking confirmed, message received, status changes).
- Realtime design documented (WebSocket/SSE/Redis pub-sub) but not required for MVP.

## 4. Constraints
- Choose realtime tech only when justified — MVP may poll/refresh.
- Only conversation participants can read/write.
- Emails via provider abstraction; no secrets client-side.

## 5. Acceptance criteria
- Participants exchange messages; notifications fire on key events; non-participants blocked.
- Notification channels are pluggable.

## 6. Files you MAY modify
- backend `messaging/`, `notifications/`
- migrations
- `../architecture/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Unit: authz + event handlers.
- Integration: message send + notification dispatch (Testcontainers, mock email).
- Authz tests.

## 9. Documentation updates required
- Update architecture/backend docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
