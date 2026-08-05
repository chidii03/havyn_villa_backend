package com.havyn.properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.properties.repo.PropertyRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full host-listing lifecycle against a real Postgres — see
 * project-docs/prompts/10-property-domain.md's acceptance criteria.
 *
 * <p><strong>Known gap this test works around:</strong> there is no self-service
 * "become a host" endpoint anywhere in the roadmap yet (granting the {@code HOST} role
 * isn't in prompt 10/11's file scope — {@code auth/}/{@code users/} are off-limits).
 * So a real user today has no product-facing way to get the {@code HOST} role. This
 * test registers a normal user via the real API (which satisfies the {@code property.
 * host_id -> app_user.id} foreign key) and then mints a HOST-scoped access token
 * directly via {@link JwtService} for that same user id — the same technique {@code
 * RbacTest} uses, just combined with a real persisted user. Production still needs a
 * real upgrade path before this feature is reachable by an actual user.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PropertyFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private PropertyRepository propertyRepositorySpy;

    @Test
    void hostCanCreateEditSubmitPublishSuspendAndManageAvailability() throws Exception {
        Host host = registerHost();

        // --- create (DRAFT) ---
        MvcResult createResult = mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createListingBody("Sunset Villa", "Lagos")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.equalTo("DRAFT")))
                .andReturn();
        String propertyId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // --- a draft listing is not publicly visible ---
        mockMvc.perform(get("/api/v1/properties/" + propertyId)).andExpect(status().isNotFound());

        // --- publishing before submitting is an invalid transition ---
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", org.hamcrest.Matchers.equalTo("INVALID_STATUS_TRANSITION")));

        // --- edit while still a draft ---
        mockMvc.perform(patch("/api/v1/host/listings/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sunset Villa (Updated)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", org.hamcrest.Matchers.equalTo("Sunset Villa (Updated)")));

        // --- another host cannot edit this listing (object-level authz) ---
        Host otherHost = registerHost();
        mockMvc.perform(patch("/api/v1/host/listings/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHost.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/host/listings/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHost.token()))
                .andExpect(status().isForbidden());

        // --- submit -> publish ---
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.equalTo("PENDING")));
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.equalTo("ACTIVE")));

        // --- now publicly visible ---
        mockMvc.perform(get("/api/v1/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", org.hamcrest.Matchers.equalTo("Sunset Villa (Updated)")));
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + propertyId + "')]").exists());

        // --- suspend hides it from the public again ---
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.equalTo("SUSPENDED")));
        mockMvc.perform(get("/api/v1/properties/" + propertyId)).andExpect(status().isNotFound());

        // --- reactivate ---
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/reactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", org.hamcrest.Matchers.equalTo("ACTIVE")));

        // --- availability: block one day, read it back ---
        mockMvc.perform(put("/api/v1/host/listings/" + propertyId + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[{\"date\":\"2026-09-01\",\"blocked\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].blocked", org.hamcrest.Matchers.equalTo(true)));
        mockMvc.perform(get("/api/v1/host/listings/" + propertyId + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .param("from", "2026-08-30")
                        .param("to", "2026-09-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date", org.hamcrest.Matchers.equalTo("2026-09-01")));

        // --- own-listings list is scoped per host ---
        mockMvc.perform(get("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + propertyId + "')]").exists());
        mockMvc.perform(get("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHost.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + propertyId + "')]").doesNotExist());
    }

    @Test
    void unauthenticatedRequestsAreRejectedAndCustomerRoleAloneIsInsufficient() throws Exception {
        mockMvc.perform(post("/api/v1/host/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createListingBody("Nope", "Lagos")))
                .andExpect(status().isUnauthorized());

        AuthResponse customer = register("customer-" + UUID.randomUUID() + "@example.com", "Just A Customer");
        mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createListingBody("Nope", "Lagos")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingWithAnUnknownPropertyTypeIsRejected() throws Exception {
        Host host = registerHost();
        String body = """
                {"typeCode":"NOT_A_REAL_TYPE","title":"X","description":"Y","address":"1 St","city":"Lagos",
                "state":"Lagos","country":"Nigeria","basePrice":1000,"capacity":2,"bedrooms":1,"beds":1,"bathrooms":1}
                """;

        mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", org.hamcrest.Matchers.equalTo("UNKNOWN_PROPERTY_TYPE")));
    }

    @Test
    void propertyDetailIsCachedAndInvalidatedOnUpdate() throws Exception {
        Host host = registerHost();
        MvcResult createResult = mockMvc.perform(post("/api/v1/host/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createListingBody("Cache Test Villa", "Lagos")))
                .andExpect(status().isCreated())
                .andReturn();
        String propertyId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/host/listings/" + propertyId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token()))
                .andExpect(status().isOk());

        org.mockito.Mockito.clearInvocations(propertyRepositorySpy);

        // two identical reads -> only the first actually hits Postgres
        mockMvc.perform(get("/api/v1/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cache Test Villa"));
        mockMvc.perform(get("/api/v1/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cache Test Villa"));
        verify(propertyRepositorySpy, times(1)).findById(any());

        // editing the listing invalidates its cache entry (PropertyChangedEvent) — the
        // very next read must see the new title, not a stale cached one. Reset again
        // first so this assertion isn't coupled to PATCH's own internal findById call
        // (via findOwned) — only the GET below is under test here.
        mockMvc.perform(patch("/api/v1/host/listings/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + host.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cache Test Villa (Renovated)\"}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.clearInvocations(propertyRepositorySpy);
        mockMvc.perform(get("/api/v1/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cache Test Villa (Renovated)"));
        verify(propertyRepositorySpy, times(1)).findById(any());
    }

    @Test
    void amenityAndPropertyTypeCatalogsArePubliclyReadable() throws Exception {
        mockMvc.perform(get("/api/v1/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='VILLA')]").exists());
        mockMvc.perform(get("/api/v1/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='WIFI')]").exists());
    }

    private Host registerHost() throws Exception {
        String email = "host-" + UUID.randomUUID() + "@example.com";
        AuthResponse registered = register(email, "Host Person");
        String hostToken = jwtService.issueAccessToken(registered.user().id(), email, Set.of("CUSTOMER", "HOST"));
        return new Host(registered.user().id(), hostToken);
    }

    private AuthResponse register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", fullName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String createListingBody(String title, String city) {
        return """
                {"typeCode":"VILLA","title":"%s","description":"A lovely place to stay.","address":"1 Beach Rd",
                "city":"%s","state":"Lagos","country":"Nigeria","basePrice":50000,"capacity":4,"bedrooms":2,
                "beds":2,"bathrooms":2,"amenityCodes":["WIFI","POOL"]}
                """.formatted(title, city);
    }

    private record Host(UUID id, String token) {
    }
}
