# Prompt 05 — Design System

> Phase: 1 · Order: 05 · Depends on: 02, 04
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Finalize implementable design tokens and the component inventory that Tailwind + shadcn/ui will realize on web (and Expo later).

## 2. Prerequisite documents
- `../design/02-design-tokens.md`
- `../design/01-design-principles.md`
- `../frontend/02-components-and-patterns.md`

## 3. Deliverables (exact)
- Locked token set (color light/dark, type scale, spacing, radius, elevation, motion, breakpoints, z-index).
- Component inventory mapped to shadcn/ui primitives + brand patterns.
- Accessibility rules per component.

## 4. Constraints
- No generic-AI/template aesthetics; restrained radius/elevation; calm motion; reduced-motion support.
- Tokens are the single source of truth — nothing hardcoded downstream.

## 5. Acceptance criteria
- Tokens are complete and pass contrast checks (WCAG 2.2 AA).
- Every MVP UI pattern maps to a documented component.

## 6. Files you MAY modify
- `../design/*`
- `../frontend/02-components-and-patterns.md`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Token contrast validation (documented).

## 9. Documentation updates required
- Update `../design/02-design-tokens.md`.

## 10. Verification before completion
- Contrast pairs verified.
- Tokens ready for Tailwind theme mapping in prompt 19.
