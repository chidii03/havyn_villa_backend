# Prompt 19 — Frontend Foundation & App Shell

> Phase: 4 · Order: 19 · Depends on: 05, 09
> Build the Next.js app + the global shell (header/nav/search/footer) and page skeletons. Original Havyn Villa design — competitor patterns studied for usability only, never copied.

## 1. Objective
Scaffold the Next.js web app and implement the **global app shell** and routing skeleton per `../frontend/03-ui-and-navigation-spec.md`: sticky header with category tabs (active = brand blue), the where/when/who search control, footer, profile menu, and all page routes as skeletons wired to the API client, theme, and auth.

## 2. Prerequisite documents
- `../frontend/01-frontend-foundation.md`, `../frontend/03-ui-and-navigation-spec.md`
- `../design/02-design-tokens.md` (blue tokens), `../architecture/03-api-design.md`
- `../../brand/` (logo.png, favicon.png, brand-colors.md)

## 3. Deliverables (exact)
- Next.js (App Router, TS) with Tailwind theme wired to the blue tokens; shadcn/ui initialized and **rebranded**; dark mode via `class`.
- **Header** (sticky, condenses on scroll): logo (links home), center **CategoryTabs** `Homes · Experiences · Services` with animated underline + **active tab in brand blue `#0B5FD0`**, right-side `Become a host` + locale + **ProfileMenu** (signed-out vs signed-in items).
- **SearchBar**: segmented Where / When / Who + brand-blue search button; each segment opens a popover (WherePanel typeahead, WhenPanel date-range calendar, WhoPanel guest steppers); expanded on `/`, compact pill on scroll/other routes; routes to `/search?...` (URL-synced). Mobile = full-screen sheet.
- **Footer**: multi-column links (Support / Hosting / Havyn Villa), bottom bar (© · Privacy · Terms · locale + currency · socials) and the tagline *"Stay beautiful, live better."*
- **Route skeletons**: `/`, `/search`, `/rooms/[id]`, `/experiences`, `/services`, `/become-a-host`, `/login`, `/signup`, `/wishlists`, `/trips`, `/messages`, `/account`, `/host`, `/admin`. Guarded routes redirect unauthenticated users; `Trips`/`Messages` appear in nav only when signed in.
- API client + TanStack Query provider; Zod + React Hook Form scaffolding; shared primitives: Skeleton, EmptyState, ErrorState, Toast.
- Auth wiring: access token in memory + refresh cookie, silent refresh, route guards, server-side protected pages.

## 4. Constraints
- Intentional **server/client split** — Server Components for data/SEO pages (`/`, `/search`, `/rooms/[id]`); client islands for search popovers, tabs, menus, forms.
- Tokens drive the theme — **no hardcoded brand values**; all active/selected/focus states use brand blue.
- No business-critical math on the frontend. No competitor markup/CSS/copy/icons — original only.
- Accessibility: keyboard-operable tabs, search popovers, calendar, menus; visible brand-blue focus rings; 44px targets.

## 5. Acceptance criteria
- App runs; header/search/footer render and are responsive; tabs highlight in brand blue and route/filter correctly; search builds a shareable `/search` URL.
- Login/refresh/logout work against the API; protected routes guarded; `Trips`/`Messages` hidden when signed out.
- Lighthouse a11y ≥ 95 on the shell; no layout shift on scroll condense.

## 6. Files you MAY modify
- `apps/web/**`, `packages/shared/**`
- `../frontend/01-frontend-foundation.md`, `../frontend/03-ui-and-navigation-spec.md` (updates)

## 7. Files you MUST NOT modify
- `apps/api/**` business logic (consume its API only)
- Any other prompt file; design tokens without an ADR

## 8. Tests required
- Component tests: CategoryTabs (active-state class = brand blue), SearchBar segment→popover, ProfileMenu auth variants, route guard.
- a11y (axe) on header, search, footer.
- API client unit tests (mocked).

## 9. Documentation updates required
- Update frontend foundation + UI spec docs with run instructions and any component API notes.

## 10. Verification before completion
- Manually verify: scroll condense, tab active-blue, search popovers + URL sync, signed-in vs signed-out nav, mobile search sheet.
- Build + tests + axe green; docs updated.
