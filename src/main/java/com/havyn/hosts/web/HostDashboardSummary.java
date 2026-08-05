package com.havyn.hosts.web;

import java.math.BigDecimal;
import java.util.List;

public record HostDashboardSummary(
        int activeListingsCount,
        int totalListingsCount,
        long upcomingReservationsCount,
        List<CurrencyAmount> totalEarnings,
        long pendingPayoutsCount,
        BigDecimal averageRating) {
}
