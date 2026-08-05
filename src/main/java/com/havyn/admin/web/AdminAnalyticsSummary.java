package com.havyn.admin.web;

import java.math.BigDecimal;

public record AdminAnalyticsSummary(
        long totalUsers,
        long totalHosts,
        long totalProperties,
        long activeProperties,
        long totalBookings,
        long confirmedOrCompletedBookings,
        BigDecimal grossRevenue,
        BigDecimal commissionCollected,
        long pendingVerificationRequests,
        long openDisputes) {
}
