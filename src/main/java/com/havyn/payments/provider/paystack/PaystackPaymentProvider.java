package com.havyn.payments.provider.paystack;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import io.micrometer.core.instrument.MeterRegistry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real Paystack REST integration (https://paystack.com/docs/api/) — "Initialize
 * Transaction" for intents (returns a hosted {@code authorization_url} to redirect
 * the guest to), webhook signature = {@code HMAC-SHA512(rawBody, secretKey)} hex,
 * compared against the {@code x-paystack-signature} header. Paystack amounts are in
 * the smallest currency unit (kobo for NGN) — our {@code numeric(12,2)} amounts are
 * always exactly 2 decimal places, so multiplying by 100 always yields a whole kobo
 * count.
 *
 * <p><strong>Known gap:</strong> there's no live Paystack account/secret key in this
 * environment, so the actual HTTP round-trips to {@code api.paystack.co} have never
 * been exercised against the real API — same category as the Docker/Maps-key gaps
 * documented elsewhere in this project. Signature verification and request/response
 * shaping are real and unit-tested against fixed HMAC values computed independently
 * of this class, not against live Paystack responses.
 */
@Component
public class PaystackPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaystackPaymentProvider.class);
    private static final String NAME = "paystack";
    private static final String SIGNATURE_HEADER = "x-paystack-signature";
    private static final BigDecimal SMALLEST_UNIT_MULTIPLIER = BigDecimal.valueOf(100);

    private final RestClient restClient;
    private final PaystackProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Takes the Spring Boot auto-configured {@link RestClient.Builder} (not a fresh
     * {@code RestClient.builder()}) specifically so tests can bind a {@code
     * MockRestServiceServer} to it and assert on the real HTTP requests this adapter
     * sends — see {@code PaystackPaymentProviderTest} — without needing a live
     * Paystack account.
     */
    public PaystackPaymentProvider(
            RestClient.Builder restClientBuilder,
            PaystackProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getSecretKey())
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PaymentIntentResult createIntent(PaymentIntentRequest request) {
        if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
            throw new ServiceUnavailableException("PAYMENT_PROVIDER_NOT_CONFIGURED", "Paystack checkout is not configured yet");
        }
        Map<String, Object> body = Map.of(
                "email", request.customerEmail(),
                "amount", String.valueOf(toSmallestUnit(request.amount())),
                "currency", request.currency(),
                "reference", request.reference(),
                "callback_url", properties.getCallbackUrl());

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/transaction/initialize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            String message = "Paystack checkout could not be started";
            try {
                JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString());
                if (error.hasNonNull("message")) {
                    message = error.path("message").asText(message);
                }
            } catch (Exception ignored) {
                // Keep the stable API error below when Paystack returns non-JSON.
            }
            throw new ServiceUnavailableException("PAYSTACK_INITIALIZE_FAILED", message);
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException(
                    "PAYSTACK_INITIALIZE_TIMEOUT",
                    "Paystack checkout timed out. Please try again in a moment.");
        }

        JsonNode data = response.path("data");
        String checkoutUrl = data.path("authorization_url").asText();
        if (checkoutUrl.isBlank()) {
            throw new IllegalStateException("Paystack did not return a hosted checkout link");
        }
        return new PaymentIntentResult(data.path("reference").asText(request.reference()), checkoutUrl);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        Map<String, Object> body = Map.of(
                "transaction", request.providerRef(),
                "amount", toSmallestUnit(request.amount()));

        JsonNode response = restClient.post()
                .uri("/refund")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        boolean succeeded = response.path("status").asBoolean(false);
        String refundRef = response.path("data").path("id").asText(null);
        return new RefundResult(refundRef, succeeded);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, HttpHeaders headers) {
        String signatureHeader = headers.getFirst(SIGNATURE_HEADER);
        if (!verifySignature(rawBody, signatureHeader)) {
            // Either a real spoofing attempt or a misconfigured secret — either way, a
            // real security-monitoring setup should alert on this.
            log.warn("Rejected {} webhook: invalid or missing signature", NAME);
            meterRegistry.counter("havyn.payment.webhook", "provider", NAME, "outcome", "invalid_signature").increment();
            throw new InvalidWebhookSignatureException();
        }

        JsonNode root = parse(rawBody);
        String event = root.path("event").asText("");
        JsonNode data = root.path("data");

        WebhookEventType type = switch (event) {
            case "charge.success" -> WebhookEventType.CHARGE_SUCCEEDED;
            case "charge.failed" -> WebhookEventType.CHARGE_FAILED;
            case "refund.processed" -> WebhookEventType.REFUND_PROCESSED;
            default -> WebhookEventType.UNKNOWN;
        };

        String reference = data.path("reference").asText(null);
        BigDecimal amount = data.hasNonNull("amount") ? fromSmallestUnit(data.path("amount").asLong()) : null;
        String currency = data.path("currency").asText(null);

        return new WebhookEvent(type, reference, amount, currency, rawBody);
    }

    boolean verifySignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String computed = hmacSha512Hex(rawBody, properties.getSecretKey());
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8), signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha512Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA512", e);
        }
    }

    private long toSmallestUnit(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).multiply(SMALLEST_UNIT_MULTIPLIER).longValueExact();
    }

    private BigDecimal fromSmallestUnit(long amount) {
        return BigDecimal.valueOf(amount).divide(SMALLEST_UNIT_MULTIPLIER, 2, RoundingMode.UNNECESSARY);
    }

    private JsonNode parse(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed webhook payload", e);
        }
    }
}
