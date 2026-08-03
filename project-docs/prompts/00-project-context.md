# Prompt 00 — Project Context (read first, every session)

> Phase: 0 · Order: 00 · Depends on: none
> Orientation only — no code. Ensures the agent internalizes the product, stack, brand, and rules before building.

## 1. Objective
Load full context for Havyn Villa before any implementation. Confirm understanding of the product, the four roles, the fixed stack, the integrations, the brand, and the canonical constraints.

## 2. Prerequisite documents
- `../../CLAUDE.md` and `../../AGENTS.md` (authoritative build instructions)
- `../00-MASTER-INDEX.md`, `../discovery/*`, `../product/*`
- `../frontend/03-ui-and-navigation-spec.md`, `../architecture/04-integrations.md`

## 3. Deliverables (exact)
- A short confirmation covering: product + tagline ("Stay beautiful, live better."), the four roles (Guest/Customer/Host/Admin), the fixed stack, integrations (Cloudinary URL-only media, Google Maps, provider-agnostic payments), and the canonical constraints.
- Only genuinely blocking questions (ideally none).

## 4. Constraints
- No code in this prompt. No stack deviation without an ADR. Brand + naming are DONE — do not re-open them.

## 5. Acceptance criteria
- Correct restatement of product, roles, stack, integrations, and constraints; only blocking questions raised.

## 6. Files you MAY modify
- None (optional scratch notes in `../discovery/`).

## 7. Files you MUST NOT modify
- Everything else, including brand assets and other prompt files.

## 8. Tests required
- None (no code).

## 9. Documentation updates required
- None.

## 10. Verification before completion
- Confirm alignment with `CLAUDE.md`/`AGENTS.md` and the master index; confirm no code was written.
