package com.havyn.payments.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentIntentRequest(@NotNull UUID bookingId) {
}
