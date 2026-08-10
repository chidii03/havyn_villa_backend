package com.havyn.notifications.web;

import com.havyn.common.web.PageResponse;

public record BookingEmailLogPage(
        long totalAttempted,
        long totalSuccessful,
        long totalFailed,
        PageResponse<BookingEmailLogSummary> logs) {
}
