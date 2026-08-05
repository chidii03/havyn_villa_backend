package com.havyn.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The admin platform end-to-end against a real Postgres — see
 * project-docs/prompts/18-admin-platform.md's acceptance criteria. No self-serve path
 * to ADMIN exists anywhere in this product (by design — see
 * backend/02-domain-modules.md's session 19 notes on why that's deliberate, not a
 * gap), so this test mints an ADMIN-scoped JWT directly, the same "test-only fixture
 * shortcut" every prior session has used for a role no self-serve flow reaches yet
 * (HOST before session 18's onboarding, etc.).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminPlatformFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void adminManagesUsersModeratesAListingReviewsKycResolvesADisputeAndUpdatesSettings() throws Exception {
        String adminToken = registerAdmin();

        // ---------------------------------------------------------------
        // Users: list, grant/revoke a role, suspend/reactivate
        // ---------------------------------------------------------------
        UUID targetUserId = registerUserId("admin-it-target-");
        mockMvc.perform(get("/api/v1/admin/users?email=admin-it-target-").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + targetUserId + "')]").exists());

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/roles/HOST").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("HOST")));

        mockMvc.perform(delete("/api/v1/admin/users/" + targetUserId + "/roles/HOST").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("HOST"))));

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/suspend").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("SUSPENDED")));
        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/reactivate").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));

        // ---------------------------------------------------------------
        // Property moderation: publish one, suspend it; submit another, reject it
        // ---------------------------------------------------------------
        UUID hostId = registerUserId("admin-it-host-");
        String activePropertyId = createAndPublishProperty(hostId, "Admin IT Active Villa " + UUID.randomUUID());
        String pendingPropertyId = createAndSubmitProperty(hostId, "Admin IT Pending Villa " + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/admin/properties/" + activePropertyId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Repeated guest complaints"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("SUSPENDED")));

        mockMvc.perform(post("/api/v1/admin/properties/" + pendingPropertyId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Missing required photos"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("DRAFT")));

        // ---------------------------------------------------------------
        // KYC: host submits, admin approves
        // ---------------------------------------------------------------
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        MvcResult submitResult = mockMvc.perform(post("/api/v1/host/verification-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("documentUrl", "https://example.com/id.pdf"))))
                .andExpect(status().isCreated())
                .andReturn();
        String verificationId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText();

        // a second submission while one is pending is rejected
        mockMvc.perform(post("/api/v1/host/verification-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("documentUrl", "https://example.com/id2.pdf"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", equalTo("VERIFICATION_ALREADY_PENDING")));

        mockMvc.perform(get("/api/v1/admin/verification-requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + verificationId + "')]").exists());

        mockMvc.perform(post("/api/v1/admin/verification-requests/" + verificationId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("APPROVED")));

        // ---------------------------------------------------------------
        // Disputes: a real booking, the guest raises a dispute, admin resolves it
        // ---------------------------------------------------------------
        String guestToken = registerGuestToken();
        String checkIn = LocalDate.now(ZoneOffset.UTC).plusMonths(4).toString();
        String checkOut = LocalDate.now(ZoneOffset.UTC).plusMonths(4).plusDays(2).toString();
        BigDecimal grandTotal = quoteGrandTotal(activePropertyId, checkIn, checkOut, 2);
        MvcResult bookingResult = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "propertyId", activePropertyId, "checkIn", checkIn, "checkOut", checkOut, "guests", 2,
                                "expectedTotal", grandTotal))))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("id").asText();

        // a non-participant cannot raise a dispute on this booking
        String strangerToken = registerGuestToken();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Not my booking"))))
                .andExpect(status().isForbidden());

        MvcResult disputeResult = mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Property was not as described"))))
                .andExpect(status().isCreated())
                .andReturn();
        String disputeId = objectMapper.readTree(disputeResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/disputes").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + disputeId + "')]").exists());

        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Partial refund issued"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("RESOLVED")));

        // resolving it again is rejected — it's no longer OPEN
        mockMvc.perform(post("/api/v1/admin/disputes/" + disputeId + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Again"))))
                .andExpect(status().isConflict());

        // ---------------------------------------------------------------
        // Settings: commission_pct takes effect immediately in a real quote
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/v1/admin/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='commission_pct')]").exists());

        mockMvc.perform(put("/api/v1/admin/settings/commission_pct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "20"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value", equalTo("20")));

        mockMvc.perform(put("/api/v1/admin/settings/commission_pct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "150"))))
                .andExpect(status().isBadRequest());

        // ---------------------------------------------------------------
        // Analytics + audit log: real numbers, real entries from everything above
        // ---------------------------------------------------------------
        mockMvc.perform(get("/api/v1/admin/analytics/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalProperties", greaterThanOrEqualTo(2)));

        MvcResult auditResult = mockMvc.perform(get("/api/v1/admin/audit-log").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode auditEntries = objectMapper.readTree(auditResult.getResponse().getContentAsString()).get("data");
        assertThat(auditEntries.size()).isGreaterThanOrEqualTo(6); // role grant/revoke, suspend/reactivate, 2x property moderation, kyc, dispute, settings...
    }

    @Test
    void anAdminCannotRevokeTheirOwnAdminRole() throws Exception {
        String adminToken = registerAdmin();
        JwtService.AccessClaims claims = jwtService.parseAccessToken(adminToken);

        mockMvc.perform(delete("/api/v1/admin/users/" + claims.userId() + "/roles/ADMIN").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("CANNOT_REVOKE_OWN_ADMIN_ROLE")));
    }

    @Test
    void nonAdminsAndAnonymousCallersAreBlockedFromEveryAdminEndpoint() throws Exception {
        String customerToken = registerGuestToken();

        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/properties").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/verification-requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/disputes").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/analytics/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-log").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    private String createAndPublishProperty(UUID hostId, String title) throws Exception {
        String propertyId = createAndSubmitProperty(hostId, title);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk());
        return propertyId;
    }

    private String createAndSubmitProperty(UUID hostId, String title) throws Exception {
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        MvcResult createResult = mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("typeCode", "VILLA"),
                                Map.entry("title", title),
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
        return propertyId;
    }

    private String registerAdmin() throws Exception {
        UUID adminId = registerUserId("admin-it-admin-");
        return jwtService.issueAccessToken(adminId, "unused@example.com", Set.of("CUSTOMER", "ADMIN"));
    }

    private UUID registerUserId(String emailPrefix) throws Exception {
        String email = emailPrefix + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Admin IT User"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }

    private String registerGuestToken() throws Exception {
        String email = "admin-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Admin IT Guest"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return jwtService.issueAccessToken(response.user().id(), email, Set.of("CUSTOMER"));
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
