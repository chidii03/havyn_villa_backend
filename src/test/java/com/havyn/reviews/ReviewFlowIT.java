package com.havyn.reviews;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reviews end-to-end against a real Postgres — see
 * project-docs/prompts/15-reviews-favorites.md's acceptance criteria. Nothing in
 * booking/ ever transitions a real booking to COMPLETED yet (a pre-existing, documented
 * gap — see backend/02-domain-modules.md's prompt 15 notes), so this test drives a
 * booking to COMPLETED directly via the repository, the same "test-only fixture
 * shortcut" technique BookingFlowIT already uses for stale-hold setup.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFlowIT {

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
    void aGuestWithACompletedStayCanReviewAndTheAggregateRatingUpdates() throws Exception {
        Property property = createActiveProperty();
        String guestToken = registerGuest();
        UUID guestId = jwtService.parseAccessToken(guestToken).userId();
        Booking booking = completedBooking(property.getId(), guestId);

        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(booking.getId(), 5, "Wonderful stay.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating", org.hamcrest.Matchers.equalTo(5)))
                .andExpect(jsonPath("$.propertyId", org.hamcrest.Matchers.equalTo(property.getId().toString())));

        mockMvc.perform(get("/api/v1/properties/" + property.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingAvg", org.hamcrest.Matchers.equalTo(5.0)))
                .andExpect(jsonPath("$.ratingCount", org.hamcrest.Matchers.equalTo(1)));

        // --- listing reviews is public, no auth needed ---
        mockMvc.perform(get("/api/v1/properties/" + property.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].comment", org.hamcrest.Matchers.equalTo("Wonderful stay.")));

        // --- a second review for the same booking is rejected ---
        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(booking.getId(), 4, "Trying again.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", org.hamcrest.Matchers.equalTo("ALREADY_REVIEWED")));
    }

    @Test
    void aGuestCannotReviewABookingThatIsNotCompleted() throws Exception {
        Property property = createActiveProperty();
        String guestToken = registerGuest();
        UUID guestId = jwtService.parseAccessToken(guestToken).userId();
        Booking pendingBooking = bookingRepository.saveAndFlush(new Booking(
                property.getId(), guestId, java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 4), 3, 2,
                BigDecimal.valueOf(30000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(30000), "NGN"));

        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(pendingBooking.getId(), 5, "Too soon.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", org.hamcrest.Matchers.equalTo("BOOKING_NOT_ELIGIBLE")));
    }

    @Test
    void aGuestCannotReviewAnotherGuestsBooking() throws Exception {
        Property property = createActiveProperty();
        String ownerToken = registerGuest();
        UUID ownerId = jwtService.parseAccessToken(ownerToken).userId();
        Booking booking = completedBooking(property.getId(), ownerId);

        String otherGuestToken = registerGuest();
        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherGuestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(booking.getId(), 5, "Not my stay.")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedReviewCreationIsRejectedButListingStaysPublic() throws Exception {
        Property property = createActiveProperty();

        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(UUID.randomUUID(), 5, "No token.")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/properties/" + property.getId() + "/reviews"))
                .andExpect(status().isOk());
    }

    private Booking completedBooking(UUID propertyId, UUID guestId) {
        Booking booking = new Booking(
                propertyId, guestId, java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2026, 6, 5), 4, 2,
                BigDecimal.valueOf(40000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(40000), "NGN");
        booking.transitionTo(BookingStatus.CONFIRMED);
        booking.transitionTo(BookingStatus.COMPLETED);
        return bookingRepository.saveAndFlush(booking);
    }

    private Property createActiveProperty() throws Exception {
        UUID hostId = registerUserId("review-it-host-");
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.ONE, null, null, null, null,
                Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private String registerGuest() throws Exception {
        String email = "review-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Review Guest"))))
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
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Review Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }

    private String reviewBody(UUID bookingId, int rating, String comment) throws Exception {
        return objectMapper.writeValueAsString(Map.of("bookingId", bookingId.toString(), "rating", rating, "comment", comment));
    }
}
