package com.havyn.booking.domain;

import java.util.Map;
import java.util.Set;

/**
 * project-docs/database/01-data-model.md#3: {@code pending/confirmed/cancelled/
 * completed/refunded}. Only {@code PENDING -> CANCELLED} is reachable end-to-end by
 * this prompt (no payment provider exists yet — prompt 13). {@code PENDING ->
 * CONFIRMED} is what a future payment webhook will call; {@code CONFIRMED ->
 * REFUNDED}/{@code COMPLETED -> REFUNDED} model what a post-payment cancellation looks
 * like so {@link CancellationPolicyCalculator} has something correct to plug into —
 * see backend/02-domain-modules.md's session 6 notes.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    REFUNDED;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(CANCELLED, COMPLETED, REFUNDED),
            CANCELLED, Set.of(),
            COMPLETED, Set.of(REFUNDED),
            REFUNDED, Set.of());

    public boolean canTransitionTo(BookingStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED || this == COMPLETED;
    }
}
