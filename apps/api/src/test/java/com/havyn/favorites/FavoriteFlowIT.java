package com.havyn.favorites;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
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

/** Favorites end-to-end against a real Postgres — see project-docs/prompts/15-reviews-favorites.md's acceptance criteria. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FavoriteFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @Test
    void aGuestCanFavoriteListAndUnfavoriteAProperty() throws Exception {
        Property property = createActiveProperty();
        String guestToken = registerGuest();

        // --- add: idempotent create, 201 ---
        mockMvc.perform(post("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyId", equalTo(property.getId().toString())));

        // --- adding the same property again is a no-op, 200 (not a duplicate row/conflict) ---
        mockMvc.perform(post("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk());

        // --- list ---
        mockMvc.perform(get("/api/v1/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.propertyId=='" + property.getId() + "')]").exists());

        // --- unfavorite ---
        mockMvc.perform(delete("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.propertyId=='" + property.getId() + "')]").doesNotExist());

        // --- unfavoriting something never favorited (or already removed) is a 404 ---
        mockMvc.perform(delete("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void favoritingANonexistentPropertyIs404() throws Exception {
        String guestToken = registerGuest();

        mockMvc.perform(post("/api/v1/favorites/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void favoritesAreScopedToTheOwningUser() throws Exception {
        Property property = createActiveProperty();
        String firstGuestToken = registerGuest();
        String secondGuestToken = registerGuest();

        mockMvc.perform(post("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstGuestToken))
                .andExpect(status().isCreated());

        // The second guest never favorited it — their list is empty, and removing it 404s (not another user's row).
        mockMvc.perform(get("/api/v1/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondGuestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.propertyId=='" + property.getId() + "')]").doesNotExist());
        mockMvc.perform(delete("/api/v1/favorites/" + property.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondGuestToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedFavoritesAccessIsRejected() throws Exception {
        Property property = createActiveProperty();

        mockMvc.perform(get("/api/v1/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/favorites/" + property.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/favorites/" + property.getId())).andExpect(status().isUnauthorized());
    }

    private Property createActiveProperty() throws Exception {
        UUID hostId = registerUserId("favorite-it-host-");
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.ONE, null, null, null, null,
                Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private String registerGuest() throws Exception {
        String email = "favorite-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Favorite Guest"))))
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
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Favorite Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }
}
