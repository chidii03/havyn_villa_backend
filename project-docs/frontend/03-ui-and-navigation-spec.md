# Frontend UI & Navigation Spec — Havyn Villa

> Status: Build spec · Owner: UI/UX + Frontend. This defines the exact app shell, navigation, search, pages, and interaction behavior for the web app. **Study leading marketplace interaction patterns for usability, but the visual design, wording, icons, spacing, and assets are 100% original Havyn Villa — do not copy any competitor's markup, CSS, copy, or imagery.**

Brand accent for all active/selected/focus states = **Havyn Blue `#0B5FD0`** (deep marine `#003488` for pressed/hover). Neutrals per `design/02-design-tokens.md`.

---

## 1. Global app shell (`apps/web/app/layout.tsx`)
Persistent chrome on every marketing/browse page: **Header → page content → Footer**. Authenticated app pages (trips, messages, host, admin) use the same header with an expanded profile menu.

### 1.1 Header (sticky, condenses on scroll)
Three zones:
- **Left:** Havyn Villa logo (`brand/logo.png`) → links home.
- **Center:** primary **category tabs** — `Homes` · `Experiences` · `Services`. The active tab shows a label + icon, an **underline indicator, and its icon/text switch to brand blue `#0B5FD0`** when selected (animated underline slide via Framer Motion). Inactive = ink-muted.
- **Right:** `Become a host` link, a globe/locale button, and a **profile menu** (avatar + hamburger) opening a dropdown.

**Scroll behavior:** at top of Home, the header is tall and shows the **expanded search bar** under the tabs. On scroll (or on non-home routes), the header condenses and the search collapses into a compact **search pill** centered in the header; clicking it re-expands the full search.

**Profile dropdown (signed out):** `Log in` (bold) · `Sign up` · divider · `Become a host` · `Help Center`.
**Profile dropdown (signed in):** `Trips` · `Messages` · `Wishlists` · `Account` · (`Host dashboard` if host) · divider · `Log out`.

### 1.2 Search bar — the "where / when / who" control
A rounded pill split into **segments**, each opening a popover panel on click (only one open at a time; outside-click closes; fully keyboard-operable, ARIA-expanded):

| Segment | Popover content |
|---|---|
| **Where** | Destination input with typeahead suggestions (cities/regions) + "Nearby" and recent searches. Feeds `destination` (and lat/lng when a suggestion is chosen). |
| **When** | Date-range calendar (check-in → check-out), 1–2 months visible, blocked/past dates disabled, flexible-dates chips optional. |
| **Who** | Guest steppers: Adults, Children, Infants, Pets, respecting property capacity later. |
| **Search** | Brand-blue circular button with search icon; on submit routes to `/search?destination&checkIn&checkOut&guests&...` (URL-synced, shareable). |

The active segment gets a raised/elevated state; the whole bar has a subtle border and shadow. Expanded (home) vs compact (scrolled) variants share one component.

### 1.3 Category tab bar (secondary, on Explore/search)
Below the header on Explore: a horizontally **scrollable row of category chips** (icon + label, e.g. Beachfront, Villas, Cabins, City, Lakefront…), with left/right scroll arrows on desktop. The **selected category turns brand blue** (filled or underlined) and filters results. Include a `Filters` button (opens a filter sheet: price range slider, type, rooms, amenities, rating, instant book).

### 1.4 Footer
Multi-column link footer (original link set):
- **Support:** Help Center, Safety, Cancellation options, Report a concern.
- **Hosting:** Become a host, Host resources, Community forum, Hosting responsibly.
- **Havyn Villa:** About, Careers, Newsroom, Investors.
- **Bottom bar:** © Havyn Villa, Privacy, Terms, Sitemap · locale + currency selector · social icons.
Tagline line: *"Stay beautiful, live better."*

---

## 2. Carousels & sliders
- **Home sections:** horizontally scrollable **property carousels** grouped by theme ("Popular in Lagos", "Beachfront escapes", "New on Havyn Villa"), each card swipeable with snap scrolling and desktop arrow controls.
- **Property card image carousel:** each card's photo area is a mini-slider (dots + arrows on hover; swipe on touch) cycling that listing's images without leaving the page.
- **Property detail hero:** gallery grid (1 large + 4 thumbnails) with a **lightbox** ("Show all photos") that is keyboard/swipe operable; videos play inline from their Cloudinary URL.
- Use an accessible, dependency-light approach (CSS scroll-snap + Embla/Framer Motion). Respect `prefers-reduced-motion`.

---

## 3. Pages / routes

### Public (guest, no auth)
- `/` **Explore / Home** — search hero + category tabs + themed carousels + map toggle.
- `/search` — results grid + **map view** (Google Maps) with price-pin markers; list/map toggle; filters sheet; URL-synced state.
- `/rooms/[id]` **Property detail** — gallery/lightbox, title, location, host card, amenities grid, house rules, reviews + rating summary, **Google Map location preview**, sticky **booking widget** (calls server `/quote`, shows server-computed total).
- `/experiences`, `/services` — category landing pages (MVP can be curated lists; architecture ready to grow).
- `/become-a-host` — host value prop + start listing.
- `/login`, `/signup`.

### Authenticated (customer)
- `/wishlists` **Wishlists** — saved properties (heart toggle everywhere); grouped lists.
- `/trips` **Trips** — upcoming/past bookings with status, receipts, cancel.
- `/messages` **Messages** — guest↔host conversations (per booking/property); appears in nav only when signed in.
- `/account` — profile, security, payment methods, notifications.

### Host
- `/host` dashboard — listings, calendar (block dates/price overrides), reservations, earnings, payouts, reviews, performance.
- `/host/listings/new` — listing wizard (basics → location(map pin) → photos/videos(Cloudinary) → amenities → rules → availability → pricing → review → publish).

### Admin
- `/admin` — moderation, disputes, KYC, users/hosts/properties, commissions/settings, analytics. RBAC-gated.

**Nav visibility rule:** `Trips` and `Messages` render in the profile menu/nav **only when authenticated**; `Wishlists` prompts login if a guest taps the heart.

---

## 4. Interaction & state details
- **Wishlist heart:** on every property card + detail; optimistic toggle via TanStack Query; unauthenticated tap opens the login modal.
- **Active/selected = brand blue** everywhere (tabs, category chips, calendar selection range, primary buttons, focused search segment).
- **Every list/grid** has loading (skeleton), empty, and error states. No layout shift.
- **Booking widget** always shows the **server** price breakdown; never computes the authoritative total client-side.
- **Accessibility:** WCAG 2.2 AA — keyboard for search popovers, calendar, carousels, lightbox, and map (list fallback). 44px touch targets. Focus rings in brand blue.
- **Responsive:** search collapses to a full-screen sheet on mobile; category chips scroll; filters and booking open as bottom sheets; carousels are swipeable.

---

## 5. Component inventory (build these, rebranded — see prompt 19/20/21)
Header, CategoryTabs, SearchBar (+ WherePanel/WhenPanel/WhoPanel), CategoryChips, FiltersSheet, PropertyCard (+ CardCarousel + WishlistHeart), CarouselRow, ResultsGrid, MapView (Google Maps) + PriceMarker, Gallery + Lightbox, BookingWidget, ReviewList + RatingSummary, HostCard, AmenitiesGrid, Footer, ProfileMenu, MessageThread, TripCard, WishlistGrid, Toast, Skeletons, EmptyState.

## 6. Implementation notes (prompt 19, session 3)

Header, CategoryTabs, SearchBar (+ its three panels), Footer, and ProfileMenu are
built — see `apps/web/src/components/shell/` and `apps/web/src/components/search/`.
Full status, deviations (static WherePanel list, placeholder CategoryIcon), and bugs
caught are in `frontend/01-frontend-foundation.md`'s "Session 3" section — not
repeated here. Two component-API notes for whoever builds prompt 20/21 next:

- **`CategoryTabs` takes a `variant?: "desktop" | "mobile"` prop** — always pass a
  distinct `variant` if you mount a second instance on the same page (each one
  renders its own `<nav>` landmark and needs a unique accessible name + Framer Motion
  `layoutId`, or you'll get an axe `landmark-unique` violation and the two instances'
  underline animations will fight each other).
- **`SearchBar` takes a `variant?: "expanded" | "compact"` prop** — both variants
  share the exact same Where/When/Who popover logic; only sizing/typography differs.
  Don't build a second, simplified "compact" implementation — extend this one.

## 7. Implementation notes (prompt 20, session 5)

`CategoryChips`, `PropertyCard` (+ `CardCarousel` + `WishlistHeart`), `CarouselRow`,
`ResultsGrid`, `FiltersSheet`, `MapView` (+ inline `PriceMarker`), `Gallery` (+
`Lightbox`), `HostCard`, `AmenitiesGrid`, `ReviewsSection` (combines the spec's
`ReviewList`/`RatingSummary` into one component — there's no review list to render
yet, see below), and `PolicyBlock` are built — see `apps/web/src/components/property/`.
Full status, the real backend gaps this session had to design around (no media/
favorites/reviews backend, no Maps key, no host display name), and bugs caught are in
`frontend/01-frontend-foundation.md`'s "Session 5" section — not repeated here.
Component-API notes for whoever builds prompt 21/22 next:

- **`CategoryChips`/`FiltersSheet` both take a `basePath` prop** (e.g. `"/"` or
  `"/search"`) and read/write the URL via `useSearchParams()` — they merge into
  whatever params already exist rather than replacing them, so mount them on any page
  that owns a `searchParams`-driven results view and they'll compose correctly with
  each other and with pagination.
- **`MapView` takes `pins: MapPin[]`, `hoveredId?`, `onHoverChange?`** — the same
  `MapPin` shape works for both the `/search` results map (many pins, hover-synced
  with `ResultsGrid`) and `/rooms/[id]`'s single-pin approximate-location preview.
  It renders its own accessible list fallback automatically whenever
  `NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY` is unset — callers don't need their own
  fallback branch.
- **`ResultsGrid` takes optional `hoveredId`/`onHoverChange`** — omit them for a plain
  grid (e.g. inside `CarouselRow`'s usage pattern); pass them to sync hover state with
  a sibling `MapView`, as `SearchResultsView` does.
- **`CardCarousel`/`Gallery` both take a `photos` prop that is always `[]` today** —
  no media backend exists yet (prompt 14). Wire real Cloudinary URLs into that prop
  when it ships; neither component needs to change.

## 8. Implementation notes (prompt 21, session 6)

`BookingWidget` is built — see `apps/web/src/components/booking/booking-widget.tsx`.
It takes `propertyId`, `capacity`, `currency`, `basePrice` and owns its own
date/guest/quote/reserve state internally (no external state needed). Full status and
the central deviation (no checkout/payment screen — prompt 13 doesn't exist yet) are
in `frontend/01-frontend-foundation.md`'s "Session 6" section. One note for whoever
builds prompt 13/22 next: **when payments land, `BookingWidget`'s `HoldConfirmation`
sub-component is where the "payment isn't available yet" copy and the `/trips` link
live — replace that block with the real checkout handoff rather than adding a second,
parallel confirmation UI.** The reserve call itself (`POST /bookings`, idempotency
key, server-breakdown rendering) doesn't need to change.

## 9. Implementation notes (prompt 22, session 8)

Section 4's "filters and booking open as bottom sheets" line is now backed by a real
component: `components/booking/mobile-booking-bar.tsx`, a `lg:hidden` fixed-bottom bar
on `/rooms/[id]` showing price/night + a "Reserve" button that opens the *same*
`BookingWidget` instance (not a duplicate) inside a bottom `Sheet`. Full rationale in
`frontend/01-frontend-foundation.md`'s "Session 8" section — not repeated here.
Component-API/gotcha notes for whoever touches shared overlay primitives or the
`Button` size scale next:

- **`Button`'s `icon` size is now `size-11` (44px), `icon-lg` is `size-12` (48px)** —
  changed from `size-8`/`size-9`. If you add a new `size="icon"` call site, it's
  already touch-target-compliant by default; only reach for `icon-sm`/`icon-xs` for
  genuinely dense/secondary UI, not primary mobile actions.
- **`Dialog`/`Sheet`/`Popover`/`DropdownMenu`/`Select` (all in `components/ui/`) now
  use this project's own z-index scale, not Tailwind's default `z-50`** —
  `Dialog`/`Sheet` overlay=`z-1300`, content=`z-1400` (above `Header`'s `z-1200`, so a
  full-height sheet or full-screen dialog no longer renders under the sticky header);
  `Popover`/`DropdownMenu`/`Select`=`z-1000` (still deliberately below the header,
  matching the documented "dropdown" tier). If you add a new full-height/full-screen
  overlay, use `Dialog`/`Sheet`, not a one-off `fixed` div with its own z-index — you'd
  silently reintroduce the same bug.
