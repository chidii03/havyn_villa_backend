package com.havyn.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
import com.havyn.payments.repo.PayoutRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.CreatePropertyRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Intent -> webhook -> booking confirmed -> payout accrued, idempotent webhook
 * replay, and cancel -> real refund call — see project-docs/prompts/13-payments.md's
 * acceptance criteria. {@link PaymentProvider} is mocked (not the real {@code
 * PaystackPaymentProvider}) since there's no live Paystack account in this
 * environment — this tests the whole orchestration (intent creation, idempotent
 * webhook handling, booking confirmation, payout accrual, refund-on-cancel) for real;
 * only the actual Paystack HTTP round-trip is out of reach here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PayoutRepository payoutRepository;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @BeforeEach
    void setUp() {
        when(paymentProvider.name()).thenReturn("paystack");
    }

    @Test
    void intentThenWebhookConfirmsTheBookingAndAccruesAPayout_andReplayIsIdempotent() throws Exception {
        UUID hostId = registerUserId("payment-it-host-");
        Property property = createActiveProperty(hostId, BigDecimal.valueOf(10000), 4);
        String guestToken = registerGuestToken();

        String checkIn = LocalDate.now(ZoneOffset.UTC).plusMonths(3).toString();
        String checkOut = LocalDate.now(ZoneOffset.UTC).plusMonths(3).plusDays(3).toString();
        BigDecimal grandTotal = quoteGrandTotal(property.getId(), checkIn, checkOut, 2);

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), checkIn, checkOut, 2, grandTotal)))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        when(paymentProvider.createIntent(any())).thenReturn(new PaymentIntentResult("ref-abc", "https://checkout.paystack.com/xyz"));

        mockMvc.perform(post("/api/v1/payments/intent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutUrl", equalTo("https://checkout.paystack.com/xyz")))
                .andExpect(jsonPath("$.provider", equalTo("paystack")));

        when(paymentProvider.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-abc", grandTotal, "NGN", "{}"));

        mockMvc.perform(post("/api/v1/payments/webhook/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(jsonPath("$.status", equalTo("CONFIRMED")));

        String period = java.time.YearMonth.now().toString();
        BigDecimal payoutAfterFirstWebhook = payoutRepository.findByHostIdAndPeriodAndCurrency(hostId, period, "NGN")
                .orElseThrow()
                .getAmount();
        assertThat(payoutAfterFirstWebhook).isGreaterThan(BigDecimal.ZERO);

        // --- a retried/duplicate webhook delivery for the same event must not double-confirm or double-accrue ---
        mockMvc.perform(post("/api/v1/payments/webhook/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk());

        BigDecimal payoutAfterReplay = payoutRepository.findByHostIdAndPeriodAndCurrency(hostId, period, "NGN")
                .orElseThrow()
                .getAmount();
        assertThat(payoutAfterReplay).isEqualByComparingTo(payoutAfterFirstWebhook);
    }

    @Test
    void cancellingAConfirmedBookingCallsTheRealProviderRefund() throws Exception {
        UUID hostId = registerUserId("payment-it-host-");
        Property property = createActiveProperty(hostId, BigDecimal.valueOf(10000), 4);
        String guestToken = registerGuestToken();

        String checkIn = LocalDate.now(ZoneOffset.UTC).plusMonths(3).toString();
        String checkOut = LocalDate.now(ZoneOffset.UTC).plusMonths(3).plusDays(3).toString();
        BigDecimal grandTotal = quoteGrandTotal(property.getId(), checkIn, checkOut, 2);

        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), checkIn, checkOut, 2, grandTotal)))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        when(paymentProvider.createIntent(any())).thenReturn(new PaymentIntentResult("ref-xyz", "https://checkout.paystack.com/xyz"));
        mockMvc.perform(post("/api/v1/payments/intent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isCreated());

        when(paymentProvider.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-xyz", grandTotal, "NGN", "{}"));
        mockMvc.perform(post("/api/v1/payments/webhook/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk());

        when(paymentProvider.refund(any())).thenReturn(new RefundResult("refund-ref-1", true));

        // FLEXIBLE (the default cancellation policy) + check-in 3 months out -> full refund.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status", equalTo("REFUNDED")))
                .andExpect(jsonPath("$.refundAmount", greaterThan(0)));

        verify(paymentProvider, times(1)).refund(any());
    }

    private Property createActiveProperty(UUID hostId, BigDecimal basePrice, int capacity) throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, basePrice, capacity, 2, 2, BigDecimal.ONE, null, null, null, null, Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private UUID registerUserId(String emailPrefix) throws Exception {
        String email = emailPrefix + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Payment Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.user().id();
    }

    private String registerGuestToken() throws Exception {
        String email = "payment-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Payment Guest"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return jwtService.issueAccessToken(response.user().id(), email, Set.of("CUSTOMER"));
    }

    private BigDecimal quoteGrandTotal(UUID propertyId, String checkIn, String checkOut, int guests) throws Exception {
        MvcResult quote = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("checkIn", checkIn, "checkOut", checkOut, "guests", guests))))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(quote.getResponse().getContentAsString()).get("grandTotal").asText());
    }

    private String bookingBody(UUID propertyId, String checkIn, String checkOut, int guests, BigDecimal expectedTotal) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "propertyId", propertyId.toString(),
                "checkIn", checkIn,
                "checkOut", checkOut,
                "guests", guests,
                "expectedTotal", expectedTotal));
    }
}
