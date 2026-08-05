package com.havyn.payments.provider;

/** {@code checkoutUrl} is where the frontend redirects the guest to complete payment on the provider's hosted page. */
public record PaymentIntentResult(String providerRef, String checkoutUrl) {
}
