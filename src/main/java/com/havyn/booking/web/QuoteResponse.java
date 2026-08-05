package com.havyn.booking.web;

import com.havyn.pricing.service.PricingBreakdown;
import java.math.BigDecimal;

/** Guest-facing breakdown — deliberately excludes {@code commissionAmount}; see {@link PricingBreakdown}'s Javadoc. */
public record QuoteResponse(
        int nights,
        BigDecimal baseTotal,
        BigDecimal cleaningFee,
        BigDecimal serviceFee,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currency) {

    public static QuoteResponse from(PricingBreakdown breakdown) {
        return new QuoteResponse(
                breakdown.nights(),
                breakdown.baseTotal(),
                breakdown.cleaningFee(),
                breakdown.serviceFee(),
                breakdown.discountTotal(),
                breakdown.taxTotal(),
                breakdown.grandTotal(),
                breakdown.currency());
    }
}
