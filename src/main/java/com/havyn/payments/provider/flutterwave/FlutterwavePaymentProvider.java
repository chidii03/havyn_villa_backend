package com.havyn.payments.provider.flutterwave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.payments.provider.InvalidWebhookSignatureException;
import com.havyn.payments.provider.PaymentIntentRequest;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.RefundRequest;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Flutterwave Standard hosted checkout adapter. Secrets never leave the backend. */
@Component
public class FlutterwavePaymentProvider implements PaymentProvider {

    private static final String NAME = "flutterwave";
    private static final String SIGNATURE_HEADER = "verif-hash";

    private final RestClient restClient;
    private final FlutterwaveProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public FlutterwavePaymentProvider(
            RestClient.Builder restClientBuilder,
            FlutterwaveProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getSecretKey())
                .build();
    }

    @Override
    public String name() { return NAME; }

    @Override
    public PaymentIntentResult createIntent(PaymentIntentRequest request) {
        requireConfigured(properties.getSecretKey(), "FLUTTERWAVE_SECRET_KEY");
        requireConfigured(properties.getRedirectUrl(), "FLUTTERWAVE_REDIRECT_URL");
        Map<String, Object> body = Map.of(
                "tx_ref", request.reference(),
                "amount", request.amount().toPlainString(),
                "currency", request.currency(),
                "redirect_url", properties.getRedirectUrl(),
                "customer", Map.of("email", request.customerEmail()),
                "customizations", Map.of("title", "Havyn Villa booking"));
        JsonNode response = restClient.post().uri("/payments").body(body).retrieve().body(JsonNode.class);
        String link = response.path("data").path("link").asText();
        if (link.isBlank()) throw new IllegalStateException("Flutterwave did not return a hosted checkout link");
        return new PaymentIntentResult(request.reference(), link);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        // Flutterwave refunds require the provider transaction id, whereas this
        // provider-agnostic model stores the merchant reference. Do not fabricate one.
        return new RefundResult(null, false);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, HttpHeaders headers) {
        requireConfigured(properties.getWebhookHash(), "FLUTTERWAVE_WEBHOOK_HASH");
        String signature = headers.getFirst(SIGNATURE_HEADER);
        if (signature == null || !MessageDigest.isEqual(
                signature.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                properties.getWebhookHash().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            meterRegistry.counter("havyn.payment.webhook", "provider", NAME, "outcome", "invalid_signature").increment();
            throw new InvalidWebhookSignatureException();
        }
        JsonNode root;
        try { root = objectMapper.readTree(rawBody); } catch (Exception ex) { throw new IllegalArgumentException("Malformed Flutterwave webhook payload", ex); }
        JsonNode data = root.path("data");
        String status = data.path("status").asText("");
        WebhookEventType type = "successful".equals(status) ? WebhookEventType.CHARGE_SUCCEEDED
                : "failed".equals(status) ? WebhookEventType.CHARGE_FAILED : WebhookEventType.UNKNOWN;
        BigDecimal amount = data.hasNonNull("amount") ? data.path("amount").decimalValue() : null;
        return new WebhookEvent(type, data.path("tx_ref").asText(null), amount, data.path("currency").asText(null), rawBody);
    }

    private static void requireConfigured(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new ServiceUnavailableException("PAYMENT_PROVIDER_NOT_CONFIGURED", "Flutterwave checkout is not configured yet");
        }
    }
}
