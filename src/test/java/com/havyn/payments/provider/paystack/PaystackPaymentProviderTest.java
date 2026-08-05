package com.havyn.payments.provider.paystack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.payments.provider.InvalidWebhookSignatureException;
import com.havyn.payments.provider.PaymentIntentRequest;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.RefundRequest;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP request-shaping + real webhook signature verification, without needing a
 * live Paystack account — {@link MockRestServiceServer} asserts on the actual request
 * this adapter builds and stubs the response, rather than treating the whole adapter
 * as untestable. See the class's own Javadoc for the (separate, real) live-credential gap.
 */
class PaystackPaymentProviderTest {

    private static final String SECRET_KEY = "sk_test_secret";

    private MockRestServiceServer mockServer;
    private PaystackPaymentProvider provider;

    @BeforeEach
    void setUp() {
        PaystackProperties properties = new PaystackProperties();
        properties.setSecretKey(SECRET_KEY);
        properties.setBaseUrl("https://api.paystack.co");
        properties.setCallbackUrl("http://localhost:3000/trips");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new PaystackPaymentProvider(builder, properties, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void name_isPaystack() {
        assertThat(provider.name()).isEqualTo("paystack");
    }

    @Test
    void createIntent_sendsAmountInKoboAndParsesTheCheckoutUrl() {
        mockServer.expect(requestTo("https://api.paystack.co/transaction/initialize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET_KEY))
                .andExpect(jsonPath("$.email").value("guest@example.com"))
                .andExpect(jsonPath("$.amount").value(3500000)) // 35000.00 NGN -> kobo
                .andExpect(jsonPath("$.reference").value("ref-123"))
                .andRespond(withSuccess(
                        """
                        {"status":true,"message":"Authorization URL created","data":
                          {"authorization_url":"https://checkout.paystack.com/abc","access_code":"abc","reference":"ref-123"}}
                        """,
                        MediaType.APPLICATION_JSON));

        PaymentIntentResult result = provider.createIntent(
                new PaymentIntentRequest(UUID.randomUUID(), BigDecimal.valueOf(35000), "NGN", "guest@example.com", "ref-123"));

        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.paystack.com/abc");
        assertThat(result.providerRef()).isEqualTo("ref-123");
        mockServer.verify();
    }

    @Test
    void refund_sendsTheOriginalReferenceAndParsesSuccess() {
        mockServer.expect(requestTo("https://api.paystack.co/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.transaction").value("ref-123"))
                .andExpect(jsonPath("$.amount").value(1000000)) // 10000.00 -> kobo
                .andRespond(withSuccess("""
                        {"status":true,"data":{"id":98765}}
                        """, MediaType.APPLICATION_JSON));

        RefundResult result = provider.refund(new RefundRequest("ref-123", BigDecimal.valueOf(10000), "Booking cancelled"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.providerRefundRef()).isEqualTo("98765");
        mockServer.verify();
    }

    @Test
    void parseWebhook_acceptsAValidSignatureAndParsesChargeSuccess() throws Exception {
        String body = """
                {"event":"charge.success","data":{"reference":"ref-123","amount":3500000,"currency":"NGN","status":"success"}}
                """;

        WebhookEvent event = provider.parseWebhook(body, signedHeaders(body));

        assertThat(event.type()).isEqualTo(WebhookEventType.CHARGE_SUCCEEDED);
        assertThat(event.providerRef()).isEqualTo("ref-123");
        assertThat(event.amount()).isEqualByComparingTo("35000.00");
        assertThat(event.currency()).isEqualTo("NGN");
    }

    @Test
    void parseWebhook_mapsChargeFailed() throws Exception {
        String body = """
                {"event":"charge.failed","data":{"reference":"ref-456"}}
                """;

        assertThat(provider.parseWebhook(body, signedHeaders(body)).type()).isEqualTo(WebhookEventType.CHARGE_FAILED);
    }

    @Test
    void parseWebhook_classifiesAnUnrecognizedEventAsUnknownRatherThanFailing() throws Exception {
        String body = """
                {"event":"subscription.create","data":{}}
                """;

        assertThat(provider.parseWebhook(body, signedHeaders(body)).type()).isEqualTo(WebhookEventType.UNKNOWN);
    }

    @Test
    void parseWebhook_rejectsATamperedBody() throws Exception {
        String originalBody = """
                {"event":"charge.success","data":{"reference":"ref-123"}}
                """;
        HttpHeaders headers = signedHeaders(originalBody);
        String tamperedBody = """
                {"event":"charge.success","data":{"reference":"ref-999"}}
                """;

        assertThatThrownBy(() -> provider.parseWebhook(tamperedBody, headers)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void parseWebhook_rejectsAMissingSignatureHeader() {
        assertThatThrownBy(() -> provider.parseWebhook("{}", new HttpHeaders())).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void parseWebhook_rejectsASignatureComputedWithTheWrongSecret() throws Exception {
        String body = """
                {"event":"charge.success","data":{"reference":"ref-123"}}
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-paystack-signature", hmacSha512Hex(body, "sk_wrong_secret"));

        assertThatThrownBy(() -> provider.parseWebhook(body, headers)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    private HttpHeaders signedHeaders(String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-paystack-signature", hmacSha512Hex(body, SECRET_KEY));
        return headers;
    }

    private static String hmacSha512Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
