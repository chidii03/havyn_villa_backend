package com.havyn.payments.provider;

import org.springframework.http.HttpHeaders;

/**
 * Provider-agnostic payment port — ADR-004. Implement one adapter per provider
 * (Paystack shipped here; Flutterwave/Stripe are structurally identical additions)
 * and select the active one via {@code havyn.payments.provider} config. Booking/
 * pricing logic never depends on a concrete provider, only this interface.
 */
public interface PaymentProvider {

    /** Matches {@code havyn.payments.provider} and the {@code {provider}} path segment in the webhook URL. */
    String name();

    PaymentIntentResult createIntent(PaymentIntentRequest request);

    RefundResult refund(RefundRequest request);

    /**
     * Verifies the webhook's signature against the raw request body and parses it.
     * Takes the full header set (not one named header pulled by the controller) since
     * each provider signs with a different header name (e.g. Paystack's {@code
     * x-paystack-signature}) — this keeps the controller provider-agnostic.
     * @throws InvalidWebhookSignatureException if the signature doesn't match.
     */
    WebhookEvent parseWebhook(String rawBody, HttpHeaders headers);
}
