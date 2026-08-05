/**
 * Self-serve "become a host" role upgrade, and host-scoped dashboard aggregation
 * (reservations across every owned listing, payouts, an earnings/performance summary)
 * — implemented in prompt 17 (session 18), see
 * project-docs/prompts/17-host-dashboard.md. Listing CRUD/availability itself lives in
 * {@code properties.web.HostListingController} (prompt 10) and photo management in
 * {@code media.web.HostMediaController} (prompt 14) — this module orchestrates across
 * those plus {@code booking}/{@code payments}, it doesn't duplicate their logic.
 *
 * <p>Not yet built (a real, separate future concern, not silently dropped): a
 * dedicated {@code HostProfile} entity and KYC (host identity verification via
 * {@code VerificationRequest}) — see database/01-data-model.md's session 4 notes on
 * why {@code property.host_id} still references {@code app_user} directly.
 */
package com.havyn.hosts;
