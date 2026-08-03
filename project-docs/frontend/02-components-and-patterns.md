# Frontend Components & Patterns

> Status: Draft · Phase 4–6. Component inventory mapped to design system.

## Primitives (shadcn/ui, rebranded)
Button, Input, Select, Checkbox, RadioGroup, Switch, Dialog, Sheet, Popover, Tooltip, Tabs, Toast, Skeleton, Badge, Avatar, Calendar/DateRange, Slider (price), Command (search).

## Patterns
- **SearchBar** (destination + date range + guests) — hero and compact/sticky variants.
- **FilterBar / FilterSheet** — price slider, type, rooms, amenities, rating; URL-synced state.
- **PropertyCard** — photo carousel, title, location, price/night, rating; favorite toggle.
- **ResultsGrid + Map** — synced list/marker hover; list fallback for a11y.
- **Gallery** — keyboard-operable lightbox, counts, video support.
- **BookingWidget** (client) — date/guest pick → calls `/quote` → shows **server** breakdown → reserve.
- **ReviewList / RatingSummary**, **HostCard**, **AmenitiesGrid**, **PolicyBlock**.
- **Dashboard shells** for host and admin (data tables, empty states, status chips).
- **Messaging thread**, **Notification menu**.

## State & forms
TanStack Query keys per resource; optimistic favorite toggle; RHF+Zod for all inputs; inline, accessible validation. No fake/frontend-only functionality — every action hits the real API.

## Motion
Framer Motion for shared-element detail transition, gallery cross-fade, sticky-header condense; all respect reduced-motion.
