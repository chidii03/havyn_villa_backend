package com.havyn.booking.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record QuoteRequest(@NotNull LocalDate checkIn, @NotNull LocalDate checkOut, @NotNull @Positive Integer guests) {
}
