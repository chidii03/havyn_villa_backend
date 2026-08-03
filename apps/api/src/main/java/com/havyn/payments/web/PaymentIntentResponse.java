package com.havyn.payments.web;

import java.util.UUID;

public record PaymentIntentResponse(UUID paymentId, String provider, String checkoutUrl) {
}
