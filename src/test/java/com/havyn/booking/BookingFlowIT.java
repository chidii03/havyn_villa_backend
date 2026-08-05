package com.havyn.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.CreatePropertyRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The booking engine end-to-end against a real Postgres + Redis — see
 * project-docs/prompts/12-booking-engine.md's acceptance criteria. Reuses the same
 * "mint a HOST-scoped JWT for a normally-registered user" technique {@code
 * PropertyFlowIT} (session 4) established, since there's still no real "become a
 * host" upgrade flow anywhere in this project.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PropertyService propertyService;

    @Test
    void guestCanQuoteReserveGetListAndCancelABooking() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 4);
        String guestToken = registerGuest();

        // --- quote is public, no auth needed ---
        MvcResult quoteResult = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody("2026-09-01", "2026-09-04", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nights", equalTo(3)))
                .andReturn();
        BigDecimal grandTotal = new BigDecimal(
                objectMapper.readTree(quoteResult.getResponse().getContentAsString()).get("grandTotal").asText());

        // --- reserve, with an idempotency key ---
        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult createResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-09-01", "2026-09-04", 2, grandTotal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andExpect(jsonPath("$.property.id", equalTo(property.getId().toString())))
                .andExpect(jsonPath("$.holdExpiresAt").exists())
                .andReturn();
        String bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // --- replaying the same idempotency key returns the SAME booking, not a new one ---
        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-09-01", "2026-09-04", 2, grandTotal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(bookingId)));

        // --- get + list ---
        mockMvc.perform(get("/api/v1/bookings/" + bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal", equalTo(grandTotal.doubleValue())));
        mockMvc.perform(get("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + bookingId + "')]").exists());

        // --- another guest cannot see or cancel this booking ---
        String otherGuestToken = registerGuest();
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherGuestToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherGuestToken))
                .andExpect(status().isForbidden());

        // --- cancel: PENDING -> CANCELLED, no refund owed (nothing was ever charged) ---
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status", equalTo("CANCELLED")))
                .andExpect(jsonPath("$.refundAmount", equalTo(0)));

        // --- cancelling an already-cancelled booking is rejected ---
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("BOOKING_NOT_CANCELLABLE")));

        // --- and the dates are free again ---
        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody("2026-09-01", "2026-09-04", 2)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAReservationWhenTheClientsExpectedTotalHasDrifted() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 4);
        String guestToken = registerGuest();

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-09-10", "2026-09-12", 2, BigDecimal.valueOf(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", equalTo("PRICE_CHANGED")));
    }

    @Test
    void rejectsAReservationExceedingCapacity() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 2);
        String guestToken = registerGuest();

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-09-10", "2026-09-12", 6, BigDecimal.valueOf(20000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("EXCEEDS_CAPACITY")));
    }

    @Test
    void anExpiredHoldIsLazilyFreedUpWhenAnotherGuestTriesToBookTheSameDates() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 4);
        UUID firstGuestId = registerHostUserId(); // just need a real app_user row for the FK
        LocalDate checkIn = LocalDate.of(2026, 12, 1);
        LocalDate checkOut = LocalDate.of(2026, 12, 5);

        // A stale hold, inserted directly (bypassing the service, whose create() always
        // sets a fresh future expiry) so its hold_expires_at is already in the past.
        Booking staleHold = new Booking(
                property.getId(), firstGuestId, checkIn, checkOut, 4, 2, BigDecimal.valueOf(40000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(40000), "NGN");
        staleHold.setHoldExpiresAt(Instant.now().minusSeconds(3600));
        Booking savedStaleHold = bookingRepository.saveAndFlush(staleHold);

        String secondGuestToken = registerGuest();
        BigDecimal total = quoteGrandTotal(property.getId(), "2026-12-01", "2026-12-05", 2);

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondGuestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-12-01", "2026-12-05", 2, total)))
                .andExpect(status().isCreated());

        Optional<Booking> reloadedStaleHold = bookingRepository.findById(savedStaleHold.getId());
        assertThat(reloadedStaleHold).isPresent();
        assertThat(reloadedStaleHold.get().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void rejectsAReservationForDatesAlreadyBooked() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 4);
        String firstGuest = registerGuest();
        String secondGuest = registerGuest();

        MvcResult quote = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody("2026-10-01", "2026-10-05", 2)))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal total = new BigDecimal(objectMapper.readTree(quote.getResponse().getContentAsString()).get("grandTotal").asText());

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstGuest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-10-01", "2026-10-05", 2, total)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondGuest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-10-02", "2026-10-06", 2, total)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", equalTo("DATES_UNAVAILABLE")));
    }

    @Test
    void unauthenticatedReservationIsRejected() throws Exception {
        Property property = createActiveProperty(BigDecimal.valueOf(10000), 4);

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(property.getId(), "2026-09-10", "2026-09-12", 2, BigDecimal.valueOf(20000))))
                .andExpect(status().isUnauthorized());
    }

    private Property createActiveProperty(BigDecimal basePrice, int capacity) throws Exception {
        UUID hostId = registerHostUserId();
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, basePrice, capacity, 2, 2, BigDecimal.ONE, null, null, null, null, Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private UUID registerHostUserId() throws Exception {
        return registerUserId("booking-it-host-");
    }

    private String registerGuest() throws Exception {
        String email = "booking-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Booking Guest"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return jwtService.issueAccessToken(response.user().id(), email, Set.of("CUSTOMER"));
    }

    private UUID registerUserId(String emailPrefix) throws Exception {
        String email = emailPrefix + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Booking Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }

    private String quoteBody(String checkIn, String checkOut, int guests) throws Exception {
        return objectMapper.writeValueAsString(Map.of("checkIn", checkIn, "checkOut", checkOut, "guests", guests));
    }

    private BigDecimal quoteGrandTotal(UUID propertyId, String checkIn, String checkOut, int guests) throws Exception {
        MvcResult quote = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(checkIn, checkOut, guests)))
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
