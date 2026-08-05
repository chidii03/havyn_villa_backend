package com.havyn.notifications;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.notifications.domain.EmailSender;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
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
 * Notification dispatch end-to-end against a real Postgres — see
 * project-docs/prompts/16-messaging-notifications.md's acceptance criteria. Drives the
 * real booking-confirm/cancel flow through the payment webhook (same technique
 * {@code PaymentFlowIT} already established: {@link PaymentProvider} mocked, no live
 * Paystack account in this environment), and asserts a real in-app {@code Notification}
 * row was persisted, not just that no exception was thrown. Email is mocked per this
 * prompt's own "Testcontainers, mock email" test requirement.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    void aConfirmedBookingNotifiesTheGuestInAppAndByEmail() throws Exception {
        when(paymentProvider.name()).thenReturn("paystack");
        UUID hostId = registerUserId("notif-it-host-");
        Property property = createActiveProperty(hostId);
        String guestEmail = "notif-it-guest-" + UUID.randomUUID() + "@example.com";
        String guestToken = registerGuest(guestEmail);

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

        when(paymentProvider.createIntent(any())).thenReturn(new PaymentIntentResult("ref-notif-1", "https://checkout.paystack.com/x"));
        mockMvc.perform(post("/api/v1/payments/intent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isCreated());

        when(paymentProvider.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-notif-1", grandTotal, "NGN", "{}"));
        mockMvc.perform(post("/api/v1/payments/webhook/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk());

        // --- the AFTER_COMMIT listener has already run by the time the webhook call returns ---
        MvcResult listResult = mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type", equalTo("BOOKING_CONFIRMED")))
                .andExpect(jsonPath("$.data[0].linkId", equalTo(bookingId)))
                .andReturn();
        String notificationId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data").get(0).get("id").asText();

        verify(emailSender).send(eq(guestEmail), anyString(), anyString());

        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", equalTo(1)));

        // --- another user can neither see nor mark-read this notification ---
        String strangerToken = registerGuest("notif-it-stranger-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // --- the owner marks it read, unread count drops to zero ---
        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", equalTo(0)));

        // --- cancelling the now-CONFIRMED booking notifies the guest again, with a refund note ---
        when(paymentProvider.refund(any())).thenReturn(new RefundResult("refund-notif-1", true));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type", equalTo("BOOKING_CANCELLED")))
                .andExpect(jsonPath("$.data[0].body", containsString("refund")));
    }

    @Test
    void aReceivedMessageNotifiesTheOtherParticipant() throws Exception {
        UUID hostId = registerUserId("notif-it-msg-host-");
        Property property = createActiveProperty(hostId);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        String guestToken = registerGuest("notif-it-msg-guest-" + UUID.randomUUID() + "@example.com");

        MvcResult startResult = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Is this available?"))))
                .andExpect(status().isCreated())
                .andReturn();
        String conversationId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asText();

        // --- the host is notified about the guest's inquiry ---
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type", equalTo("MESSAGE_RECEIVED")))
                .andExpect(jsonPath("$.data[0].linkId", equalTo(conversationId)));
    }

    private Property createActiveProperty(UUID hostId) throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.ONE, null, null, null, null,
                Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private UUID registerUserId(String emailPrefix) throws Exception {
        String email = emailPrefix + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Notification Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }

    private String registerGuest(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Notification Guest"))))
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
