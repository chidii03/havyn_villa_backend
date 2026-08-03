package com.havyn.payments.provider.flutterwave;

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
import com.havyn.payments.provider.WebhookEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FlutterwavePaymentProviderTest {

    private MockRestServiceServer mockServer;
    private FlutterwavePaymentProvider provider;

    @BeforeEach
    void setUp() {
        FlutterwaveProperties properties = new FlutterwaveProperties();
        properties.setSecretKey("FLWSECK_TEST");
        properties.setWebhookHash("webhook-secret");
        properties.setBaseUrl("https://api.flutterwave.com/v3");
        properties.setRedirectUrl("http://localhost:3000/trips");
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        provider = new FlutterwavePaymentProvider(builder, properties, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void createIntent_createsAHostedCheckoutLink() {
        mockServer.expect(requestTo("https://api.flutterwave.com/v3/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer FLWSECK_TEST"))
                .andExpect(jsonPath("$.tx_ref").value("ref-123"))
                .andExpect(jsonPath("$.amount").value("35000"))
                .andRespond(withSuccess("{\"status\":\"success\",\"data\":{\"link\":\"https://checkout.flutterwave.com/pay/abc\"}}", MediaType.APPLICATION_JSON));

        var result = provider.createIntent(new PaymentIntentRequest(
                UUID.randomUUID(), BigDecimal.valueOf(35000), "NGN", "guest@example.com", "ref-123"));

        assertThat(result.providerRef()).isEqualTo("ref-123");
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.flutterwave.com/pay/abc");
        mockServer.verify();
    }

    @Test
    void parseWebhook_acceptsConfiguredHashAndMapsSuccess() {
        String body = "{\"data\":{\"status\":\"successful\",\"tx_ref\":\"ref-123\",\"amount\":35000,\"currency\":\"NGN\"}}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("verif-hash", "webhook-secret");

        var event = provider.parseWebhook(body, headers);

        assertThat(event.type()).isEqualTo(WebhookEventType.CHARGE_SUCCEEDED);
        assertThat(event.providerRef()).isEqualTo("ref-123");
        assertThat(event.amount()).isEqualByComparingTo("35000");
    }

    @Test
    void parseWebhook_rejectsInvalidHash() {
        assertThatThrownBy(() -> provider.parseWebhook("{}", new HttpHeaders()))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
