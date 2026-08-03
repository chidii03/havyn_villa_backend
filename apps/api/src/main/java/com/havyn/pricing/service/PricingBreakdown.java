package com.havyn.pricing.service;

import java.math.BigDecimal;

/**
 * The authoritative price breakdown — see project-docs/product/02-user-stories-and-acceptance.md
 * US-B1 for the exact guest-facing field list (nights, base, cleaning, service,
 * discounts, taxes, total). {@code commissionAmount} is deliberately NOT part of the
 * guest-facing total (see project-docs/product/03-business-model.md#17: commission is
 * a host-payout deduction, not an added guest charge) — it's carried here so {@code
 * BookingService} can persist it on the {@code Booking} row for later payout
 * accounting (prompt 17), without exposing it via {@code POST /quote}'s response.
 */
public record PricingBreakdown(
        int nights,
        BigDecimal baseTotal,
        BigDecimal cleaningFee,
        BigDecimal serviceFee,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal commissionAmount,
        String currency) {
}
