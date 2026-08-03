package com.havyn.messaging;

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
import com.havyn.notifications.domain.EmailSender;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Messaging end-to-end against a real Postgres — see
 * project-docs/prompts/16-messaging-notifications.md's acceptance criteria. Email is
 * mocked per this prompt's own explicit test requirement ("Testcontainers, mock
 * email") — Postgres/Redis stay real via Testcontainers, but {@link EmailSender} is
 * replaced so message-triggered notifications don't need a real SMTP server, mirroring
 * {@code MediaFlowIT}'s {@code @MockitoBean MediaStorage} pattern.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MessagingFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @MockitoBean
    private EmailSender emailSender;

    @Test
    void aGuestAndHostCanExchangeMessagesAndNonParticipantsAreBlocked() throws Exception {
        UUID hostId = registerUserId("messaging-it-host-");
        Property property = createActiveProperty(hostId);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        String guestToken = registerGuest();

        // --- guest starts a conversation (nested creation requires auth) ---
        MvcResult startResult = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Is this available in June?"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyTitle", equalTo(property.getTitle())))
                .andReturn();
        String conversationId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("id").asText();

        // --- creation without a token is rejected, even though GET .../properties/** is public ---
        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "No token"))))
                .andExpect(status().isUnauthorized());

        // --- host sees it in their inbox, replies ---
        mockMvc.perform(get("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + conversationId + "')]").exists());

        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Yes, June is open!"))))
                .andExpect(status().isCreated());

        // --- full thread is readable by either participant, in order ---
        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", equalTo(2)))
                .andExpect(jsonPath("$.data[1].body", equalTo("Yes, June is open!")));

        // --- guest marks the thread read ---
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk());

        // --- a non-participant cannot read or write ---
        String strangerToken = registerGuest();
        mockMvc.perform(get("/api/v1/conversations/" + conversationId).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Butting in"))))
                .andExpect(status().isForbidden());

        // --- unauthenticated access is rejected outright ---
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void aHostCannotStartAConversationOnTheirOwnListing() throws Exception {
        UUID hostId = registerUserId("messaging-it-self-host-");
        Property property = createActiveProperty(hostId);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));

        mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Talking to myself"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("HOST_CANNOT_MESSAGE_OWN_LISTING")));
    }

    @Test
    void repeatedInquiriesFromTheSameGuestReuseOneThread() throws Exception {
        UUID hostId = registerUserId("messaging-it-reuse-host-");
        Property property = createActiveProperty(hostId);
        String guestToken = registerGuest();

        MvcResult first = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "First message"))))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult second = mockMvc.perform(post("/api/v1/properties/" + property.getId() + "/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Second message"))))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        assertThat(secondId).isEqualTo(firstId);
        mockMvc.perform(get("/api/v1/conversations/" + firstId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", equalTo(2)));
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

    private String registerGuest() throws Exception {
        String email = "messaging-it-guest-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Messaging Guest"))))
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
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Messaging Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return UUID.fromString(user.get("id").asText());
    }
}
