/**
 * RayProp wholesale-inventory integration (https://rayprop.io/docs) — imports
 * verified shortlet listings into our own {@code property}/{@code property_media}
 * tables via {@link com.havyn.properties.rayprop.RayPropSyncService}, rather than
 * proxying RayProp live: Postgres stays the single source of truth (CLAUDE.md), so
 * search/caching/rate-limiting keep working unchanged for these listings.
 *
 * <p>Read-only in this integration: the sandbox key only grants GET access (search,
 * availability, quotes) — creating reservations/holds needs a live key and isn't part
 * of this import path.
 */
package com.havyn.properties.rayprop;
