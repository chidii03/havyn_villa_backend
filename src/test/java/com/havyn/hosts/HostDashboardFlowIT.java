package com.havyn.hosts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.web.AuthResponse;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * The host dashboard end-to-end against a real Postgres — see
 * project-docs/prompts/17-host-dashboard.md's acceptance criteria: onboarding, listing
 * publish (via the existing {@code HostListingController}, prompt 10), a real guest
 * reservation, and accurate earnings/payouts once a payment webhook confirms it.
 * {@link PaymentProvider} is mocked — same "no live Paystack account in this
 * environment" reasoning as {@code PaymentFlowIT}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HostDashboardFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @Test
    void aHostOnboardsPublishesAListingAndSeesAnAccurateDashboard() throws Exception {
        when(paymentProvider.name()).thenReturn("paystack");

        // --- register, but onboarding is rejected until the email is verified ---
        String email = "host-dashboard-it-" + UUID.randomUUID() + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Future Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse registered = objectMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponse.class);
        String customerToken = registered.accessToken();

        mockMvc.perform(post("/api/v1/host/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("EMAIL_NOT_VERIFIED")));

        // --- test-only fixture shortcut for email verification, same category as
        // BookingFlowIT's stale-hold / ReviewFlowIT's directly-constructed COMPLETED
        // booking — there's no real product flow in this sandbox to click a real
        // verification email link through Mailhog.
        User user = userRepository.findById(registered.user().id()).orElseThrow();
        user.markEmailVerified(Instant.now());
        userRepository.saveAndFlush(user);

        // --- onboarding now succeeds, granting HOST without a full re-login ---
        MvcResult onboardResult = mockMvc.perform(post("/api/v1/host/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", hasSize(2)))
                .andReturn();
        AuthResponse onboarded = objectMapper.readValue(onboardResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(onboarded.user().roles()).containsExactlyInAnyOrder("CUSTOMER", "HOST");
        String hostToken = onboarded.accessToken();

        // --- onboarding is idempotent: calling it again just re-issues tokens ---
        mockMvc.perform(post("/api/v1/host/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", hasSize(2)));

        // --- a brand-new host has an honest, empty dashboard ---
        mockMvc.perform(get("/api/v1/host/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalListingsCount", equalTo(0)))
                .andExpect(jsonPath("$.totalEarnings", empty()));

        // --- create, submit, and publish a listing through the existing host listing API ---
        MvcResult createResult = mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("typeCode", "VILLA"),
                                Map.entry("title", "Dashboard IT Villa " + UUID.randomUUID()),
                                Map.entry("description", "A lovely place to stay."),
                                Map.entry("address", "1 Beach Rd"),
                                Map.entry("city", "Lagos"),
                                Map.entry("state", "Lagos"),
                                Map.entry("country", "Nigeria"),
                                Map.entry("basePrice", 10000),
                                Map.entry("capacity", 4),
                                Map.entry("bedrooms", 2),
                                Map.entry("beds", 2),
                                Map.entry("bathrooms", 2)))))
                .andExpect(status().isCreated())
                .andReturn();
        String propertyId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/submit").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/host/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalListingsCount", equalTo(1)))
                .andExpect(jsonPath("$.activeListingsCount", equalTo(1)));

        // --- a guest reserves it (real booking engine, PENDING — no payment yet) ---
        String guestToken = registerGuest();
        String checkIn = LocalDate.now(ZoneOffset.UTC).plusMonths(3).toString();
        String checkOut = LocalDate.now(ZoneOffset.UTC).plusMonths(3).plusDays(3).toString();
        BigDecimal grandTotal = quoteGrandTotal(propertyId, checkIn, checkOut, 2);
        MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "propertyId", propertyId, "checkIn", checkIn, "checkOut", checkOut, "guests", 2,
                                "expectedTotal", grandTotal))))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asText();

        // --- the host sees the reservation immediately, PENDING, with resolved guest/property names ---
        mockMvc.perform(get("/api/v1/host/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", equalTo(bookingId)))
                .andExpect(jsonPath("$.data[0].status", equalTo("PENDING")))
                .andExpect(jsonPath("$.data[0].guestName", equalTo("Reservation Guest")));

        // --- a different host sees nothing — object-level isolation, not just a filter bug ---
        String otherHostToken = onboardNewHost("host-dashboard-it-other-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/v1/host/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));
        mockMvc.perform(get("/api/v1/host/payouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));

        // --- confirm payment via the real webhook path -> booking CONFIRMED, payout accrued ---
        when(paymentProvider.createIntent(any())).thenReturn(new PaymentIntentResult("ref-host-dash-1", "https://checkout.paystack.com/x"));
        mockMvc.perform(post("/api/v1/payments/intent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isCreated());
        when(paymentProvider.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-host-dash-1", grandTotal, "NGN", "{}"));
        mockMvc.perform(post("/api/v1/payments/webhook/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/host/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status", equalTo("CONFIRMED")));

        mockMvc.perform(get("/api/v1/host/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnings[0].currency", equalTo("NGN")))
                .andExpect(jsonPath("$.totalEarnings[0].amount", org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.pendingPayoutsCount", equalTo(1)));

        mockMvc.perform(get("/api/v1/host/payouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", equalTo("PENDING")));
    }

    @Test
    void nonHostsAndAnonymousCallersAreBlockedFromHostEndpoints() throws Exception {
        String email = "host-dashboard-it-nonhost-" + UUID.randomUUID() + "@example.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Just A Guest"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse registered = objectMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/host/dashboard/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/host/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/host/payouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/host/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/host/onboarding")).andExpect(status().isUnauthorized());
    }

    private String onboardNewHost(String email) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Other Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse registered = objectMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponse.class);
        User user = userRepository.findById(registered.user().id()).orElseThrow();
        user.markEmailVerified(Instant.now());
        userRepository.saveAndFlush(user);

        MvcResult onboardResult = mockMvc.perform(
                        post("/api/v1/host/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(onboardResult.getResponse().getContentAsString(), AuthResponse.class).accessToken();
    }

    private String registerGuest() throws Exception {
        String email = "host-dashboard-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Reservation Guest"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.accessToken();
    }

    private BigDecimal quoteGrandTotal(String propertyId, String checkIn, String checkOut, int guests) throws Exception {
        MvcResult quote = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("checkIn", checkIn, "checkOut", checkOut, "guests", guests))))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(quote.getResponse().getContentAsString()).get("grandTotal").asText());
    }
}
