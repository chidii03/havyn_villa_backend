package com.havyn.payments.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentIntentRequest(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String reference) {
}
