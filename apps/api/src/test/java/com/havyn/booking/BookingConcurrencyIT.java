package com.havyn.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.booking.domain.Booking;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.CreatePropertyRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ADR-008 / US-B2: "Given two concurrent bookings for overlapping dates, When both
 * attempt to confirm, Then exactly one succeeds." Two independent proofs: the real
 * HTTP path (Redis lock does the serializing in practice) and a direct-repository path
 * that bypasses the lock entirely to prove the Postgres exclusion constraint — the
 * documented backstop — genuinely enforces this on its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingConcurrencyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void exactlyOneOfSeveralParallelReservationsForTheSameOverlappingDatesSucceeds() throws Exception {
        Property property = createActiveProperty();
        int attempts = 6;
        List<String> guestTokens = IntStream.range(0, attempts).mapToObj(i -> guestToken()).collect(Collectors.toList());

        String checkIn = "2026-11-01";
        String checkOut = "2026-11-05";
        BigDecimal total = quoteGrandTotal(property.getId(), checkIn, checkOut, 2);

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Integer>> tasks = guestTokens.stream()
                    .<Callable<Integer>>map(token -> () -> mockMvc.perform(post("/api/v1/bookings")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bookingBody(property.getId(), checkIn, checkOut, 2, total)))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .toList();

            List<Future<Integer>> futures = pool.invokeAll(tasks);
            long successCount = 0;
            long conflictCount = 0;
            for (Future<Integer> future : futures) {
                int status = future.get(30, TimeUnit.SECONDS);
                if (status == HttpStatus.CREATED.value()) {
                    successCount++;
                } else if (status == HttpStatus.CONFLICT.value()) {
                    conflictCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(attempts - 1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void thePostgresExclusionConstraintAloneRejectsAnOverlappingBookingBypassingTheApplicationLockEntirely() throws Exception {
        // Real property_id/guest_id FK targets are required — the constraint being
        // tested is booking's own overlap exclusion, not the FKs, but both are
        // enforced together so the rows have to be genuinely valid otherwise.
        Property property = createActiveProperty();
        UUID guestId = registerRealUserId();

        Booking first = newBooking(property.getId(), guestId, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15));
        Booking overlapping = newBooking(property.getId(), guestId, LocalDate.of(2027, 1, 12), LocalDate.of(2027, 1, 18));

        bookingRepository.saveAndFlush(first);

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Booking newBooking(UUID propertyId, UUID guestId, LocalDate checkIn, LocalDate checkOut) {
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        Booking booking = new Booking(
                propertyId, guestId, checkIn, checkOut, nights, 2, BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(50000), "NGN");
        booking.setHoldExpiresAt(Instant.now().plusSeconds(900));
        return booking;
    }

    private UUID registerRealUserId() {
        try {
            String email = "concurrency-it-" + UUID.randomUUID() + "@example.com";
            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Concurrency Guest"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
            return response.user().id();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Property createActiveProperty() throws Exception {
        UUID hostId = registerRealUserId();
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.ONE, null, null, null, null, Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private String guestToken() {
        try {
            String email = "concurrency-it-guest-" + UUID.randomUUID() + "@example.com";
            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Concurrency Guest"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
            return jwtService.issueAccessToken(response.user().id(), email, Set.of("CUSTOMER"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal quoteGrandTotal(UUID propertyId, String checkIn, String checkOut, int guests) throws Exception {
        MvcResult quote = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("checkIn", checkIn, "checkOut", checkOut, "guests", guests))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").exists())
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
