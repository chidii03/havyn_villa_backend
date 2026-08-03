package com.havyn.booking.domain;

import java.math.BigDecimal;

/**
 * Pure refund-percentage calculation for the three cancellation policies (labels
 * mirror {@code apps/web/src/components/property/policy-block.tsx}):
 * <ul>
 *   <li>{@code FLEXIBLE} — full refund if cancelled at least 1 day before check-in.</li>
 *   <li>{@code MODERATE} — full refund if cancelled at least 5 days before check-in.</li>
 *   <li>{@code STRICT} — 50% refund if cancelled at least 7 days before check-in.</li>
 * </ul>
 * "24 hours before check-in" (the product copy for {@code FLEXIBLE}) is approximated
 * as "the day before or earlier" — {@code Property}/{@code Booking} store check-in as
 * a plain date with no check-in time, so a hard "24 hours" boundary isn't
 * representable; day-granularity is the honest precision available here.
 */
public final class CancellationPolicyCalculator {

    private static final BigDecimal FULL_REFUND = BigDecimal.valueOf(100);
    private static final BigDecimal HALF_REFUND = BigDecimal.valueOf(50);
    private static final BigDecimal NO_REFUND = BigDecimal.ZERO;

    private CancellationPolicyCalculator() {
    }

    /** @param daysUntilCheckIn whole days between "now" and check-in; negative/zero means check-in has passed or is today. */
    public static BigDecimal refundPercentage(String cancellationPolicy, long daysUntilCheckIn) {
        return switch (cancellationPolicy) {
            case "FLEXIBLE" -> daysUntilCheckIn >= 1 ? FULL_REFUND : NO_REFUND;
            case "MODERATE" -> daysUntilCheckIn >= 5 ? FULL_REFUND : NO_REFUND;
            case "STRICT" -> daysUntilCheckIn >= 7 ? HALF_REFUND : NO_REFUND;
            default -> NO_REFUND;
        };
    }
}
