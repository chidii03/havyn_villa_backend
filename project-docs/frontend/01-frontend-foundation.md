# Frontend Foundation — Next.js Web

> Status: Draft · Phase 4 · Owner: Senior Next.js Engineer.

## Stack (fixed)
Next.js (App Router), React, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, React Hook Form, Zod, Framer Motion.

**Icons (updated session 3):** app-level UI icons go through a single registry —
`components/ui/icon-registry.ts` (semantic name → `@phosphor-icons/react` component)
+ `components/ui/icon.tsx` (`<Icon name="..." active weight="duotone" />`, applies the
brand-blue-on-active rule). `iconsax-react` was evaluated for a "premium rounded"
nav treatment but hasn't been published since 2022 (abandoned) — Phosphor's
`duotone`/`fill` weights cover that need instead; see icon-registry.ts's own comment.
shadcn's vendored primitive internals (sheet/dialog/dropdown-menu/calendar/command
close/chevron/check glyphs) are intentionally left on `lucide-react` — they're
generated files, not app-level icon choices; hand-editing them drifts from
`shadcn add --overwrite`.

## Server/client boundaries (intentional)
- **Server Components by default** for data-fetching, SEO-critical pages (home, search results, property detail).
- **Client Components** only where interactivity is required (booking widget, filters, gallery, forms, maps, messaging).
- Do NOT make everything a client component. Keep data fetching on the server where possible; hydrate islands of interactivity.

## Data layer
- Server: fetch from the Spring API in Server Components / route handlers.
- Client: **TanStack Query** for interactive/mutating data (favorites, booking, messaging) with caching, retries, optimistic updates where safe.
- **Zod** schemas shared for form + API response validation; **React Hook Form** for all forms.

## Structure
```
app/
 ├─ (marketing)/            home, about
 ├─ (search)/search         results + map
 ├─ properties/[id]         detail (server) + booking widget (client)
 ├─ (auth)/login,register
 ├─ trips/                  traveler bookings
 ├─ host/                   dashboard, listings, reservations, earnings
 ├─ admin/                  moderation, disputes, settings, analytics
 └─ api/                    route handlers/proxies as needed
components/  (ui = shadcn, patterns, property, booking, ...)
lib/         (api client, auth, query client, zod schemas, format)
```

## Theming
Tailwind theme wired to `design/02-design-tokens.md` tokens (colors, spacing, radius, motion). Dark mode via `class` strategy. shadcn/ui components restyled to brand — not left default.

## Auth on web
Access token in memory; refresh token in secure httpOnly cookie; silent refresh; route guards for authed areas; server-side session checks for protected pages.

## Performance
Image optimization (next/image + object-storage CDN), route-level code splitting, streaming/suspense for search, prefetch on hover, skeleton loaders. Target good Core Web Vitals.

## DoD (frontend feature)
Server/client split justified; Zod-validated forms; loading/empty/error states; accessible (keyboard+contrast+focus); responsive; tests (component + a11y); no business-critical math client-side.

## Session 1 / prompt 08 (bootstrap) — status and deviations

**Monorepo:** npm workspaces at the repo root (`apps/web`, `packages/shared`) — no
pnpm/turborepo/yarn. `apps/api` (Java/Gradle) is not part of the npm workspace.

**Next.js 16 / React 19 / Tailwind v4.** create-next-app's own scaffold ships an
`AGENTS.md`/`CLAUDE.md` inside `apps/web` warning that this Next.js version has
"breaking changes vs. your training data" — real ones encountered so far: Tailwind v4
is CSS-first (**no `tailwind.config.js`** — theme tokens live in
`src/app/globals.css` under `@theme`), and `next.config.ts` needed an explicit
`turbopack.root` (this machine has an unrelated stray lockfile in the Windows user
profile that Next's workspace-root auto-detection otherwise picks up).

**shadcn/ui** initialized on the new `base-nova` preset, which is built on
**`@base-ui/react`, not Radix UI** — component internals (e.g. `Button`) differ from
older shadcn/Radix examples. `components.json` config: `style: base-nova`,
`baseColor: neutral` (recolored via CSS variables below), `iconLibrary: lucide`.

**Brand tokens** wired into `globals.css` `:root`/`.dark` from
`design/02-design-tokens.md` (`--brand #0B5FD0`, `--brand-hover #003488`, `--ink`,
`--ink-muted`, `--bg`, `--surface`, `--line`, `--success`/`--warning`/`--danger`,
`--sun`), mapped through shadcn's semantic slots (`--primary`, `--secondary`,
`--muted`, `--accent`, `--destructive`, `--ring`, `--radius`) so every shadcn
component is brand-colored by default, not left neutral. Fonts: `Inter` as
`--font-sans` (UI), `Fraunces` as `--font-display` (headings), both via `next/font/google`.

**⚠️ Brand asset gap found (not fixed here — brand/naming are DONE per CLAUDE.md,
this is a design-kit follow-up, not a coding task):** `brand/logo-wordmark.svg` is a
leftover export from the **superseded green palette** (`brand/brand-colors.md` says
the blue system "supersedes the earlier green concept") — its baked-in `#1E5B4F`/
`#E8734A` fill would render off-brand, so it is **not used** anywhere in the app (not
copied into `public/`). `brand/logo.png`/`favicon.png` are the correct Havyn Blue mark,
but both are square badges with an **opaque background** (no transparent horizontal
lockup exists). The current header (`components/brand/logo.tsx`) works around this by
using `logo.png` as a small rounded badge next to a typeset "Havyn Villa" wordmark
(Fraunces + Inter, brand tokens) — a real horizontal SVG/PNG lockup with a transparent
background should be exported and dropped in before this goes further.

**What's implemented:** branded shell (`src/app/page.tsx`) — sticky header with the
logo treatment above, hero with tagline, two CTAs, footer; `QueryProvider`
(TanStack Query, one `QueryClient` per session); `MotionFadeIn` (Framer Motion,
respects `prefers-reduced-motion` via `useReducedMotion()`); shadcn `Button`;
react-hook-form/zod/@hookform/resolvers installed and ready but not yet exercised by
a real form (no login/search form exists yet — that's prompt 19/09). Test tooling:
Vitest + jsdom + React Testing Library + `jest-axe`, one smoke suite
(`src/app/page.test.tsx`) asserting the brand/tagline/CTAs render and zero axe
violations.

**Known gap:** `npm audit` reports 15 dev-dependency advisories (transitive, in
eslint's `minimatch` chain, `postcss`, and the `shadcn` CLI's MCP SDK dependency) with
no non-breaking fix available yet (`npm audit fix` finds nothing to do; `--force`
would downgrade Next.js itself to `9.x`, which is wrong — not applied). Revisit when
upstream patches land.

### Running locally
```
npm run dev -w apps/web      # http://localhost:3000
npm run build -w apps/web    # production build
npm run lint -w apps/web
npm run test -w apps/web     # Vitest — no Docker needed
```

## Session 3 / prompt 19 (app shell) — status and deviations

**Implemented:** global `Header` (sticky, condenses on scroll, `CategoryTabs` with an
animated brand-blue underline, `SearchBar` with real Where/When/Who popovers, mobile
search as a bottom `Sheet`, `ProfileMenu` with signed-out/signed-in variants incl.
role-gated Host/Admin links), `Footer`, all 14 route skeletons from the prompt plus
`/help`, `/forgot-password`, `/reset-password`, `/verify-email` (added because
`SmtpMailer` — apps/api, prompt 09 — already links to the latter two; `/help` because
the footer/profile-menu link to it). `/login` and `/signup` are fully functional
(real RHF+Zod forms against the real backend), not just skeletons — the prompt's
acceptance criteria require login/refresh/logout to actually work. `/account` is also
real (GET/PATCH `/me` already exists). `AuthProvider` (in-memory access token, silent
refresh on mount + before every expiry — see its own doc comment for the known
multi-tab race), a two-layer route guard (`requireSessionCookie` server fast-path +
`RequireAuth` client authoritative check — see both files' comments for why a single
server-side check can't work here), API client (`lib/api/http.ts` + `lib/api/auth.ts`).

**Deviations:**
- **WherePanel is a static Lagos-first destination list, not Google Places
  Autocomplete** — no `NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY` is provisioned yet.
- **CategoryIcon renders a Phosphor duotone icon, not real 3D/Lottie art** — that
  requires actual licensed asset files a coding pass can't generate or source; see
  `public/brand/category-icons/README.md` for how to drop real art in later.
- **Icon system**: see the Stack section above (Phosphor over the unmaintained
  iconsax-react).

**Bugs caught by actually running the tests, not just building:**
- `apiFetch` crashed with a raw `SyntaxError` instead of a clean `ApiError` when a
  failure response isn't valid JSON (e.g. an HTML error page from a proxy on a 502) —
  fixed to catch the parse failure and fall back to `UNKNOWN_ERROR`.
- base-ui's `Menu.GroupLabel` (`DropdownMenuLabel`) throws `MenuGroupContext is
  missing` unless wrapped in `Menu.Group` (`DropdownMenuGroup`) — unlike Radix, which
  allows a label with no group wrapper. This silently broke `ProfileMenu`'s signed-in
  variant (the popup crashed on open, so it just looked "stuck closed").
- `Header` mounts two responsive copies of `CategoryTabs` (desktop/mobile, CSS-toggled)
  — both rendering `<nav aria-label="Primary">` is an axe `landmark-unique` violation;
  `CategoryTabs` now takes a `variant` prop so each gets a distinct accessible name
  (and a distinct Framer Motion `layoutId`, so the two underline animations don't
  fight over shared state).
- `EmptyState`/`ErrorState` rendered an `<h3>` directly under each page's `<h1>` with
  nothing in between — an axe `heading-order` violation across every route skeleton;
  changed to `<h2>`. `/help` and `/rooms/[id]` had no `<h1>` at all (added, `sr-only`
  where there's no visible title).
- Base-ui's `Menu` positions its popup via floating-ui (rAF/`ResizeObserver`), which
  resolves asynchronously even in jsdom — tests asserting on popup content must use
  `findBy*`/`waitFor`, not `getBy*`, right after the triggering click, or they flake.
- Vitest wasn't resetting the DOM or mock call history between tests in the same file
  (`@testing-library/react`'s Vitest integration needs an explicit
  `afterEach(cleanup)`, unlike Jest) — added globally in `vitest.setup.ts` alongside
  `vi.clearAllMocks()`. Explains several "toHaveBeenCalled" and "element not found"
  failures that had nothing to do with the component under test.

**Known limitation (documented in code, not fixed):** refresh-token rotation is
per-tab — two tabs refreshing around the same moment can race, and the loser gets
signed out as if its token were reused. Needs cross-tab leader election
(`BroadcastChannel`/Web Locks) to fix properly; out of scope for this pass.

**Known gap:** the Testcontainers-only backend tests (session 2) still need Docker,
same as before — unrelated to this session, unchanged.

## Session 5 / prompt 20 (property discovery UI) — status and deviations

**Implemented:** `/` (Explore), `/search` (results grid + Google Maps view + list/map
toggle with hover-sync), and `/rooms/[id]` (gallery/lightbox, host card, amenities,
rating summary, policy block, approximate-location map preview, sticky booking-widget
slot) all rewritten from the prompt 19 route skeletons to consume the real property/
search API (prompt 10/11) — no mock or hardcoded listing data anywhere. New shared
types in `packages/shared/src/property.ts`; new client (`lib/api/properties.ts`,
`lib/api/search.ts`); new components under `components/property/`: `CategoryChips`,
`CardCarousel`, `PropertyCard`, `WishlistHeart`, `CarouselRow`, `ResultsGrid` (+
skeleton), `FiltersSheet`, `MapView`, `Gallery`/`Lightbox`, `HostCard`,
`AmenitiesGrid`, `ReviewsSection`, `PolicyBlock`, `SearchResultsView`. New deps:
`@vis.gl/react-google-maps`, shadcn `slider`/`checkbox`/`select`. `loading.tsx` added
for `/search` and `/rooms/[id]` — skeletons (`components/ui/skeleton.tsx`), never
spinners, per this project's standing instruction.

**Deviations — all driven by real, current backend/data-model gaps, not shortcuts:**
- **No photos anywhere yet.** `PropertyDetail`/`SearchResultItem` have zero media
  fields (prompt 14 — Cloudinary — hasn't shipped). `CardCarousel` and `Gallery` are
  fully built (swipe, arrows, dots, keyboard nav, video support) and already take a
  `photos`/real prop shape; today that prop is always `[]`, which renders an honest "no
  photos yet" placeholder, not a broken image or a stock photo standing in for a real
  one.
- **`WishlistHeart` doesn't persist anything.** There's no favorites backend (prompt
  15) and no login-modal component anywhere in this app. A signed-out tap redirects to
  `/login?redirect=`; a signed-in tap shows a toast ("Wishlists are launching soon")
  instead of flipping to a permanently-"saved" heart that would silently un-save
  itself on the next reload — that would be lying about persisted state, not a
  simplification.
- **`ReviewsSection` never fetches or renders a review list.** There's no `GET
  /properties/{id}/reviews` endpoint (prompt 15). `ratingAvg`/`ratingCount` are real
  fields, always 0 today since nothing has been reviewed — the empty state reflects
  that honestly.
- **`HostCard` shows a generic placeholder, not a host name.** `PropertyDetail` only
  exposes `hostId`; there's no public host-profile field or lookup endpoint, and this
  prompt's `apps/api/**` scope is consume-only. See backend/02-domain-modules.md's
  session 4 "become a host" gap, which this is downstream of.
- **`MapView` renders its accessible list fallback by default in this environment** —
  `NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY` still isn't provisioned (same gap
  WherePanel hit in session 3). The fallback isn't just an error path here; it's what
  actually renders. Added `NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID` to `.env.example` alongside
  it — `AdvancedMarker`'s custom price-pin content needs a Cloud-configured Map ID,
  which `architecture/04-integrations.md` didn't originally list. The Maps-configured
  code path (real `@vis.gl/react-google-maps` rendering, not the fallback) has **not**
  been visually verified against live Google Maps — there's no key to test it with —
  only unit-tested with the library mocked.
- **Marker clustering deferred** (`@googlemaps/markerclusterer`) — with zero real
  listings possible yet (no host-role upgrade path — session 4's other gap), there's
  nothing to cluster. Add it when result density justifies the dependency.
- **CategoryChips filters by the real `PropertyType` taxonomy**, not the UI spec's
  example labels ("Beachfront, Villas, Cabins, City, Lakefront…") — those aren't
  fields anywhere in the data model. Using them would mean building a filter UI that
  can't actually filter anything.
- **Only one themed carousel row** ("New on Havyn Villa", sort=newest) instead of the
  spec's several example rows ("Popular in Lagos", "Beachfront escapes") — those need
  data dimensions (city-level popularity, a beachfront tag) that don't exist yet.
- **FiltersSheet drops "instant book"** — no such field on `Property`.
- **E2E scope narrowed.** Playwright isn't set up anywhere in this repo yet (prompt
  23's job); standing it up from scratch here would be its own significant piece of
  scope. The required "search → filter → open detail" flow is instead covered by
  component/integration tests asserting the real navigation contract — CategoryChips'
  and the results grid's links resolve to the correct URLs, FiltersSheet builds the
  correct query string — rather than a real multi-page browser session. Revisit with
  actual Playwright E2E in prompt 23.

**Bugs/design issues caught by actually running the tests, not just building:**
- `MapView` originally read `NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY` at module scope,
  which would have made the "has key" branch untestable with `vi.stubEnv` (ES module
  imports are evaluated once, before any test code runs, so a later env stub can't
  retroactively change an already-captured `const`). Moved the read inside the
  component function body — same real behavior (Next.js inlines `NEXT_PUBLIC_*` vars
  at build time regardless of read location), but now testable without
  `vi.resetModules()` gymnastics.
- A `/search` page test asserting `getByText("Sunset Villa")` failed with "multiple
  elements found" — not a bug, but a reminder that `MapView`'s list fallback and the
  results grid are both fully present in the DOM at once (Tailwind's `hidden lg:block`
  responsive classes hide one of them visually; jsdom doesn't evaluate media queries,
  so both render). Switched to `getAllByText`.
- jsdom doesn't implement `Element.scrollTo`/`scrollBy` (throws "not implemented") —
  needed by `CardCarousel`, `CategoryChips`, `CarouselRow`, and `Gallery`'s arrow/touch
  navigation. Added a global no-op stub in `vitest.setup.ts` alongside the existing
  `matchMedia` shim.

## Session 6 / prompt 21 (booking & checkout UI) — status and deviations

**Implemented:** `BookingWidget` (`components/booking/booking-widget.tsx`) — date
range (reuses `WhenPanel`) + guest stepper (capped at the listing's capacity), a real
`POST /quote` call rendering the exact server breakdown, and a real `POST /bookings`
reserve action with a per-selection `Idempotency-Key`. Wired into `/rooms/[id]`,
replacing the prompt-19/20 placeholder slot. `/trips` rewritten from its route
skeleton to real `GET /bookings` + a real cancel action (`POST /bookings/{id}/
cancel`), with toast confirmation and query invalidation. New shared types
(`packages/shared/src/booking.ts`) and `lib/api/bookings.ts`.

**The central deviation, stated plainly:** prompt 21 depends on prompt 13
(payments), which per `AGENTS.md`'s own session plan doesn't exist until *next*
session. So there is deliberately **no checkout/payment screen** here — no fake "Pay
now" button, no fake provider redirect, no fake "Booking confirmed" page. Reserving
creates a real, server-verified `PENDING` hold (real Redis lock, real Postgres
exclusion constraint, real price re-verification), and `BookingWidget` stops at an
honest **"Dates held — payment isn't available yet"** state. `/trips` shows real
booking data with real status labels (`Awaiting payment` for `PENDING`, not a fake
"Confirmed"), not the spec's "upcoming/past" framing, since nothing can be
`CONFIRMED` yet. This is the same category of decision as session 4's host-role gap
and session 5's wishlist/reviews gaps — a structural cross-prompt dependency, not a
shortcut.

**Other deviations:**
- **E2E scope narrowed, same as session 5.** Playwright still isn't set up in this
  repo (prompt 23's job). `BookingWidget`'s test isolates its own logic by mocking
  `WhenPanel` (a shared, separately-relevant component) rather than driving
  `react-day-picker`'s real day-cell UI through RTL — the existing `search-bar.test.tsx`
  already made the same call for the same reason (day-cell accessible names are
  relative-to-"today" and awkward to assert reliably).
- **`react-hooks/set-state-in-effect` caught a real anti-pattern**, not just a lint
  nag: the first draft reset `idempotencyKey`/`reserveState`/`heldBooking` inside a
  `useEffect` keyed on the date/guest selection, which the newer eslint-plugin-react-
  hooks now flags as a cascading-render risk. Rewritten using React's documented
  "adjusting state during render" pattern (track the previous selection key in state,
  compare and reset synchronously in the render body) instead of an effect — see the
  component's own comment.

**Bugs/design issues caught by actually running the tests, not just building:**
- Two page/component tests failed with "multiple elements found" for repeated
  fixture text (`Sunset Villa` appearing in more than one `TripCard`, and in both the
  results grid and `MapView`'s list fallback on `/search` — same root cause pattern
  session 5 already documented). Fixed with `findAllByText`/more specific queries,
  not by weakening the assertions.
- `rooms/[id]/page.test.tsx` needed updating for the new `BookingWidget` — it now
  needs a `QueryClientProvider` in the test tree (the widget uses TanStack Query) and
  mocks for `useAuth`/`useRouter`, which the route skeleton's original test never
  needed. The stale `"Booking opens soon"` assertion (from the session-5 placeholder)

## Session 8 / prompt 22 (responsive mobile web) — status and deviations

**Method note, stated up front:** this environment has no browser/screenshot tool —
the tool list is Agent, Artifact, AskUserQuestion, Bash, Edit, Glob, Grep, PowerShell,
Read, ReportFindings, ScheduleWakeup, Skill, ToolSearch, Write. "Responsive audit"
here means a systematic **code-level review** (Tailwind classes, breakpoint
modifiers, rendered DOM structure, jsdom-testable assertions) across home/`/search`/
`/rooms/[id]`/booking/`/trips`, not a live-viewport walkthrough. Flagged transparently
rather than silently claimed as visually verified — same discipline as sessions 5/6's
"no Google Maps key" and "no Docker" notes.

**Touch targets (project's own 44×44px floor, `design/04-accessibility.md` —
stricter than WCAG 2.2 AA's 24px SC 2.5.8):**
- `components/ui/button.tsx`'s shared `icon` size variant was `size-8` (32px) —
  bumped to `size-11` (44px); `icon-lg` bumped `size-9`→`size-12` to keep the scale
  ordered (it has no live call sites yet, so this is scale-coherence, not a live-bug
  fix). `icon-xs`/`icon-sm` left alone — legitimately smaller sizes for dense/
  secondary UI, not used for primary mobile actions. Verified narrow, known blast
  radius first (exactly 5 app-level `size="icon"` call sites, grepped before
  changing): Header's mobile search trigger, FiltersSheet's bedroom stepper,
  BookingWidget's guest stepper, SearchBar's submit button — all primary controls,
  none dense/decorative.
- `search/who-panel.tsx`'s guest steppers moved off `icon-sm` (28px) onto the now-
  fixed `icon` (44px), for consistency with the other stepper patterns.
- `property/wishlist-heart.tsx` (every `PropertyCard`'s heart, always visible on
  mobile — no hover-gating) — `size-8`→`size-11`, icon glyph 16→18px to match.
- `property/gallery.tsx`'s Lightbox prev/next buttons (always visible in the
  full-screen modal, not hover-gated) — `size-10` (40px)→`size-11` (44px).
- `property/search-results-view.tsx`'s mobile-only (`lg:hidden`) List/Map toggle —
  had no explicit height (`py-1.5` only); given `min-h-11` + `px-4`. This is the sole
  primary control exclusive to mobile on `/search`.
- `(protected)/trips/page.tsx`'s `TripCard` Cancel button — a primary mobile action
  (cancelling a real `PENDING` hold) at the shared Button's `sm` size (28px); given a
  `min-h-11` override on that call site only, not a global change to `sm` (which is
  used elsewhere for legitimately compact/secondary UI not yet fully audited).
- `property/card-carousel.tsx`'s hover-gated prev/next arrows were invisible AND
  unreachable on touch (no `:hover`) while still being real tab stops — a distinct
  bug class from raw sizing (focusable-but-invisible). Left at `size-7` (not a
  mobile-primary control; native swipe via CSS scroll-snap is) but added
  `focus-visible:opacity-100` so keyboard users can actually see what they've
  tabbed to.

**A real, previously undiscovered bug, found while checking `Sheet`'s internals for
the bottom-sheet pattern:** every shadcn-generated overlay primitive — `dialog.tsx`,
`sheet.tsx`, `popover.tsx`, `dropdown-menu.tsx`, `select.tsx` — used plain Tailwind
`z-50`, while this app's own sticky `Header` uses the project's custom z-scale at
`z-1200`. Since 50 < 1200, any full-height/full-screen overlay rendered **behind**
the header wherever they visually overlapped it: `FiltersSheet` (`side="right"`,
`inset-y-0`, full viewport height) had its own title bar obscured by the site header;
`Gallery`'s full-screen Lightbox (the "touch-optimized gallery" this prompt names
explicitly) had the header floating on top of the photo at the top of the screen.
Fixed by aligning these primitives to the project's own documented scale
(`design/04-accessibility.md`'s z-index table): `Dialog`/`Sheet` overlay→`z-1300`
("overlay" tier), content→`z-1400` ("modal" tier, above header); `Popover`/
`DropdownMenu`/`Select`→`z-1000` ("dropdown" tier — deliberately still below the
header, matching the documented scale's own ordering). Not testable via jsdom
(no real layout/paint), so this is a code-level fix verified by reading the
resulting stacking order, not a passing test — flagged instead of silently assumed.

**Mobile booking CTA — the prompt's "no layout that hides critical price/CTA on
mobile" constraint, applied for real:** below `lg`, `/rooms/[id]`'s grid collapses to
one column and `BookingWidget` (previously un-hidden, just non-sticky) rendered
inline *after* gallery/host/amenities/map/policy — several screens down, with no
persistent price or Reserve CTA visible on load. Added `components/booking/
mobile-booking-bar.tsx`: a `lg:hidden` fixed-bottom bar showing price/night + a
"Reserve" button that opens the *same* `BookingWidget` in a bottom `Sheet` (not a
second, state-duplicating instance — the desktop sidebar copy is now `hidden
lg:block`, only one of the two is ever mounted at a time). Content area gets
`pb-24 lg:pb-0` so the fixed bar doesn't cover the last section.

**Skip-to-content link** — `app/layout.tsx` had none (a real, direct gap against
`design/04-accessibility.md`'s explicit requirement). Added a `sr-only
focus-visible:not-sr-only` link before `Header`, targeting a new `id="main-content"`
on `<main>`.

**Not fixed, and why (avoiding scope creep beyond what's actually broken):**
- `carousel-row.tsx`/`category-chips.tsx`'s scroll arrows are `hidden ... sm:flex` —
  desktop-only, no mobile touch-target exposure.
- Footer's link grid (`grid-cols-2` at mobile) wasn't bumped to 44px-tall rows —
  dense footer link lists below 44px are a near-universal, accepted pattern (Stripe,
  Airbnb, etc. all do this); treating every footer link as a "primary" touch target
  would look oversized/unprofessional for what is deliberately low-frequency
  navigation.
- `MapView`'s price-pin markers stay small — map pin density (many pins, no overlap)
  is a legitimate, industry-standard reason to keep individual pins below 44px; the
  required a11y list fallback (`MapListFallback`) already exists as the accessible
  alternative. Separately noted but **not fixed**: with no Google Maps key configured
  in this environment (same gap `map-view.tsx`'s own doc comment already states),
  `MapListFallback` is what real users see when tapping "Map" on `/search` today, and
  its rows aren't links to the property — a functional gap tied to the missing Maps
  key (prompt 20's original scope), not a responsive/breakpoint issue, so left alone
  here rather than expanded into out-of-scope work.
- `who-panel.tsx`, home page, `map-view.tsx`, `footer.tsx`, and the auth pages
  (`login`/`signup` via `AuthPageShell`) were read in full and found to already be
  correctly responsive — no changes needed there beyond the stepper-size fix above.

**Tests:** new `mobile-booking-bar.test.tsx`, `who-panel.test.tsx`, and
`search-results-view.test.tsx` (none of these three existed before this session —
real, pre-existing coverage gaps, not just missing touch-target assertions).
Regression assertions (asserting the `size-11`/`min-h-11`/`focus-visible:opacity-100`
classes directly) added to the existing `wishlist-heart.test.tsx`, `gallery.test.tsx`,
and `card-carousel.test.tsx`. **Stated honestly: jsdom has no CSS engine, so none of
this asserts a real rendered pixel size or an actual viewport breakpoint** — every
"responsive" assertion here is a DOM-class-presence proxy, consistent with how this
project has documented the same jsdom limitation every session since session 3.
**E2E on a real mobile viewport (Playwright) is still deferred to prompt 23** — same
gap sessions 5 and 6 already documented; still not set up in this repo.
`npm run build`, `npm run lint`, and `npm run test` (×3 for flakiness) all pass —
76 tests total (was 62 before this session).
  was replaced with an assertion against the real widget's initial state.

## Session 18 / prompt 17 (host dashboard) — status and deviations

**Implemented:** `/host` (real dashboard home — 4 backend-computed stat cards, an
honest empty state when a host has zero listings), `/host/listings` (list + status-
transition actions — Submit/Publish/Suspend/Reactivate, each a real call into
`HostListingController`, prompt 10), `/host/listings/new` (a single sectioned
create-listing form covering all 19 `CreatePropertyRequest` fields), `/host/listings/
[id]/calendar` (blocking dates + per-date price overrides, built on the existing
`Calendar`/`react-day-picker` primitive — no new date library added), `/host/
reservations`, `/host/earnings`. `/become-a-host` is no longer a dead-end marketing
page — its CTA is now context-aware (logged-out → `/signup`, unchanged; signed-in
non-host → a real `POST /host/onboarding` call; existing host → straight to `/host`).

**Deviations:**
- **`(protected)/host/layout.tsx` adds a HOST role gate exactly like `admin/
  layout.tsx`'s pattern** (`<RequireAuth role="HOST" roleRedirectTo="/become-a-host">`)
  — the one difference from admin's is `roleRedirectTo`: a non-host bounced off
  `/host` lands on the actual next step for them (become a host), not the generic `/`
  default `RequireAuth` otherwise uses. Unlike `trips`/`admin` (single-page routes),
  `/host` is a multi-page section, so this layout also owns shared chrome (a new
  `HostNav` section-nav component) that every `/host/*` page renders under — a
  deliberate, scoped exception to this codebase's usual "each page wraps itself"
  convention, not an oversight.
- **`AuthContextValue` gained one new method, `applySession`** — every existing
  session-setting path (`login`, `register`, `silentRefresh`) duplicated the same
  three-setter sequence (`setAccessToken`/`setUser`/`scheduleRefresh`) inline;
  `/host/onboarding` needed that same sequence for a *fourth* call site
  (re-minted tokens after a role grant), so it was extracted into one named method
  instead of a fourth inline copy. `become-a-host/page.tsx` is `applySession`'s only
  caller today.
- **Two new Phosphor icons added to the registry** (`wallet`, `pencil`, `trash`,
  `check` — `icon-registry.ts`): no money/wallet glyph existed before this session,
  needed for the earnings stat card; the other three are held in reserve for the
  same host-dashboard surface's obvious next actions (edit/delete a listing) that
  aren't wired to a real mutation yet — added to the single source-of-truth registry
  now rather than as one-off imports later, per that file's own stated purpose.
- **The create-listing form uses `Controller` (react-hook-form) for the two
  non-native-input fields** (property type `Select`, amenity `Checkbox` list) while
  plain `register()` covers every text/number field — same hybrid pattern
  `filters-sheet.tsx` already uses for its own `Select`/`Checkbox` fields, not a new
  one introduced here.
- **`createPropertySchema` uses `z.coerce.number()` for numeric fields**, which
  required *not* explicitly annotating `useForm<CreatePropertyInput>` (the schema's
  Zod-inferred *input* type — pre-coercion — differs from its *output* type, and
  `zodResolver` types against the former; explicitly pinning the generic to the
  output type broke `tsc`). Removed the explicit generic and let it infer correctly
  instead — a real type error hit and fixed during this session, not a hypothetical.
- **A single sectioned form, not a multi-step wizard**, for `/host/listings/new` —
  see backend/02-domain-modules.md's session 18 notes for the reasoning (no existing
  Stepper primitive in this codebase; this prompt's acceptance criteria never asks
  for a bespoke onboarding wizard experience).
- **The earnings page states plainly that every payout is "Pending"** — there is no
  payout-execution rail yet (`PayoutStatus`'s own Javadoc, session 7), so this page
  never implies money has moved, only that it's accrued and owed. Matches this
  project's standing "no fake production data / no false confidence" discipline.

**New E2E coverage, closing a gap `e2e/README.md` had named every session since
prompt 23:** `host-listing-and-reservation.spec.ts` drives the *entire* new flow for
real — including real email verification via Mailhog's REST API
(`helpers.ts#findVerificationLink` reads the actual email `SmtpMailer` sent and
clicks the actual link; nothing here is bypassed), onboarding, create → submit →
publish, a second guest (separate browser context) reserving it, and the host seeing
the real reservation. Stops at the same honest `PENDING`/"Awaiting payment" boundary
every payment-touching spec in this suite already stops at (no live Paystack
anywhere this project has run). `e2e/README.md` updated to match — its "Host
publish... not built" and "Review eligibility... doesn't exist yet" entries were
already stale before this session (reviews shipped in prompt 15/session 16) and are
corrected now, not just for the part this session closed.

**Verification:** `npm run typecheck`, `npm run lint`, and `npm run build` all pass.
`npx vitest run` — 140 tests across 39 files pass (was 128/36 before this session; 12
new: `host/page.test.tsx`, `host/listings/page.test.tsx`, `become-a-host/
page.test.tsx`, each including an `axe()` accessibility assertion). The new
Playwright spec was **not executed in this dev sandbox** — same "no Docker daemon
here" limitation documented every session since session 4 (Postgres/Redis/Mailhog
can't run locally); it does compile clean under `tsc` and follows the exact
structure of the specs that already run for real in `ci.yml`'s `e2e` job.

## Session 19 / prompt 18 (admin platform) — status and deviations

**Implemented:** `/admin` (real dashboard home — 7 backend-computed stat cards from
`GET /admin/analytics/summary`), `/admin/users` (search, role grant/revoke toggles,
suspend/reactivate — the self-revoke-your-own-admin-role guard is disabled, not just
server-rejected, on the button itself), `/admin/properties` (list every listing
regardless of status; suspend/reject through a shared reason-prompt dialog),
`/admin/verification-requests` (KYC queue — approve, or reject through the same
dialog), `/admin/disputes` (open-dispute queue — resolve/dismiss through the same
dialog), `/admin/settings` (edit `commission_pct` and any other `platform_setting`
row inline), `/admin/audit-log` (browse the real audit trail). `admin/layout.tsx` —
already had the real `<RequireAuth role="ADMIN">` gate since session 3/prompt 19
(there was simply nothing behind it); now there is. Same "shared chrome for a
multi-page section" pattern `host/layout.tsx` established (a new `AdminNav`
component), for the same reason.

**Deviations:**
- **One new shared component, `ModerationReasonDialog`** — properties (suspend/
  reject), KYC (reject), and disputes (resolve/dismiss) all needed the exact same
  "confirm this action with a required reason" interaction. Built once
  (`components/admin/moderation-reason-dialog.tsx`), not copy-pasted three times —
  the confirm button stays disabled until a reason is typed, both server-side *and*
  client-side reflecting the backend's `@NotBlank` constraint on the same field.
- **No frontend for a guest/host to *raise* a dispute** — `DisputeController`'s
  `POST /bookings/{id}/disputes` is real and tested at the API level
  (`AdminPlatformFlowIT`), but this session's frontend scope prioritized the admin
  side, which is what this prompt's actual acceptance criteria names ("Admin can...
  resolve disputes"). Flagged here and in `e2e/README.md`, not silently skipped —
  the new E2E spec seeds a real dispute via a direct API call in its own setup
  rather than pretending a raise-dispute UI exists.
- **Admin property moderation reuses the existing `PropertySummary`/`PropertyDetail`
  shared types** (`packages/shared/src/property.ts`) rather than adding
  admin-specific duplicates — `AdminPropertyController` returns the exact same DTOs
  the public/host controllers do.
- **`/admin/settings`' inline edit only enables Save once the value actually
  changed** (`dirty` check against the loaded value) — a small, deliberate UX
  guard against accidentally re-submitting an unchanged commission rate.

**New E2E coverage, closing the last of the four gaps `e2e/README.md` has named
since prompt 23** (host-publish closed in session 18, admin-moderate now in this
one): `admin-moderate-and-resolve.spec.ts` logs in as a seeded fixture admin (see
`apps/web/e2e/seed.ts#seedAdminFixture` — registers for real through the live API so
the password hashes correctly with the app's own Argon2id encoder, then grants
ADMIN via one SQL statement, the one step with no self-serve equivalent anywhere in
this product by design), suspends the seeded fixture listing through the real
`/admin/properties` UI, and resolves a real dispute (raised via a direct API call in
the spec's own setup, not a UI — see above) through the real `/admin/disputes` UI.
`seed.ts`'s and its own top-of-file docblock's stale "prompt 17 not built" framing
(already outdated since session 18) is corrected in the same pass.

**Verification:** `npm run typecheck`, `npm run lint`, and `npm run build` all pass.
`npx vitest run` — 149 tests across 42 files pass (was 140/39 before this session; 9
new: `admin/page.test.tsx`, `admin/users/page.test.tsx`,
`admin/properties/page.test.tsx`, each including an `axe()` accessibility assertion
and, for the properties page, a real dialog-interaction test — typing a reason,
confirming the button stays disabled until one exists). The new Playwright spec
was **not executed in this dev sandbox**, same documented limitation as every
session since session 4.

## Session 20 / prompt 30 (Expo mobile) — gap found in this codebase, not fixed here

While building `apps/mobile`'s messaging screens (a real prompt-30 deliverable —
"messaging" is named explicitly in that prompt's core flows), found that
`(protected)/messages/page.tsx` **on web** is still exactly the `EmptyState` stub it
was scaffolded as, unrelated to this session's own work: "Guest ↔ host messaging per
booking/property lands with prompt 16." Prompt 16 shipped the messaging *backend*
(session 17); no session since has built the web frontend for it, so web has had zero
working consumer of that backend the entire time. Mobile's
`app/(tabs)/messages.tsx` + `app/conversation/[id].tsx` (in `apps/mobile`, this
session) are the first real frontend for it, on either platform. Left as-is here —
out of prompt 30's file scope (`apps/mobile`, shared package, `../mobile/*` docs only)
— but worth its own prompt: build `(protected)/messages/page.tsx` for real against the
same `GET/POST /conversations` endpoints mobile now proves out.
