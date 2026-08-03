package com.havyn.booking.service;

import com.havyn.booking.domain.Booking;
import java.math.BigDecimal;

public record CancellationOutcome(Booking booking, BigDecimal refundPercentage, BigDecimal refundAmount) {
}
