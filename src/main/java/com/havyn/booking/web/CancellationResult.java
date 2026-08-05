package com.havyn.booking.web;

import java.math.BigDecimal;

/**
 * {@code refundPercentage}/{@code refundAmount} are always {@code 0} for a {@code
 * PENDING} booking cancelled today (nothing was ever charged — no payment provider
 * exists yet, prompt 13). The fields are real and policy-computed via {@code
 * CancellationPolicyCalculator} so the {@code CONFIRMED -> REFUNDED} path already
 * works correctly once payments land — see backend/02-domain-modules.md's session 6
 * notes.
 */
public record CancellationResult(BookingDetail booking, BigDecimal refundPercentage, BigDecimal refundAmount) {
}
