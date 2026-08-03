# Prompt 20 — Property Discovery UI (cards, carousels, detail, Google Maps)

> Phase: 5 · Order: 20 · Depends on: 11, 19
> Build Explore/search/detail using real APIs. Original design; competitor patterns for usability only.

## 1. Objective
Implement the discovery experience per `../frontend/03-ui-and-navigation-spec.md`: home/Explore with category chips + themed carousels, search results grid + **Google Maps** view with price pins, and the property detail page with gallery/lightbox, amenities, reviews, map location, and the wishlist heart — all consuming the search/property APIs.

## 2. Prerequisite documents
- `../frontend/03-ui-and-navigation-spec.md`, `../frontend/02-components-and-patterns.md`
- `../architecture/04-integrations.md` (Google Maps + Cloudinary delivery), `../architecture/03-api-design.md`

## 3. Deliverables (exact)
- **Home/Explore**: scrollable **CategoryChips** (icon + label; **selected chip = brand blue**, filters results), themed **CarouselRow** sections of PropertyCards, and a Filters button (FiltersSheet: price slider, type, rooms, amenities, rating, instant book).
- **PropertyCard**: image **CardCarousel** (swipe + hover arrows + dots, Cloudinary `f_auto,q_auto` sizes), **WishlistHeart** (optimistic; login modal if guest), location, price/night (tabular), rating.
- **/search**: responsive results grid + **MapView (Google Maps)** with clustered **PriceMarker** pins synced to cards (hover highlights both); list/map toggle; a11y list fallback; URL-synced filters/pagination.
- **/rooms/[id]**: gallery grid + **Lightbox** ("Show all photos", keyboard/swipe; inline video from Cloudinary URL), title/location, HostCard, AmenitiesGrid, house rules, ReviewList + RatingSummary, **Google Map** approximate-location preview, and the sticky **BookingWidget** slot (implemented in prompt 21).
- Google Maps loaded via referrer-restricted browser key; Places Autocomplete already feeds the search "Where" panel.

## 4. Constraints
- Guests browse all discovery pages without auth; wishlist/booking require auth.
- Server Components for `/` and `/rooms/[id]` (SEO); client islands for carousels, map, filters, heart, lightbox.
- Detail map shows an **approximate area** (privacy) until booked. Maps browser key referrer-restricted; **no server/secret keys on the client**.
- Media via Cloudinary CDN URLs only. Brand blue for all active/selected states. WCAG 2.2 AA.

## 5. Acceptance criteria
- Users search, filter via chips/sheet, and open a real listing; results grid ↔ map pins stay in sync; category chip active state is brand blue.
- Card carousels and detail lightbox work by keyboard, touch, and mouse; video plays from URL.
- Filter/category state is shareable via URL; loading/empty/error states present.

## 6. Files you MAY modify
- `apps/web/**` (discovery routes + components), `packages/shared/**` (types)
- `../frontend/*` (doc updates)

## 7. Files you MUST NOT modify
- `apps/api/**` search/property logic (consume only)
- Any other prompt file; design tokens without an ADR

## 8. Tests required
- Component: CategoryChips (active=brand blue + filter), CardCarousel, WishlistHeart (optimistic + auth gate), FiltersSheet, Lightbox, MapView (mocked Maps).
- a11y (axe) on Explore, results, detail; verify map list-fallback.
- E2E: search → filter by category → open detail.

## 9. Documentation updates required
- Update frontend UI docs; note Maps usage and Cloudinary delivery sizes.

## 10. Verification before completion
- Verify list↔map sync, chip active-blue, carousel/lightbox/video, wishlist optimistic + login gate.
- Build + tests + axe + E2E green; docs updated.
