package com.havyn.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.web.AuthResponse;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.AvailabilityDayInput;
import com.havyn.properties.web.CreatePropertyRequest;
import com.havyn.properties.web.SetAvailabilityRequest;
import com.havyn.search.repo.PropertySearchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code GET /search} against a real Postgres + Redis — filters, availability
 * exclusion, pagination, sort, and the Redis cache's hit/miss + generation-based
 * invalidation. See project-docs/prompts/11-search-discovery.md's acceptance criteria.
 * Each test uses a randomized "city" per fixture set so tests can't see each other's
 * data despite sharing one Testcontainers Postgres/Redis for the whole class.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SearchFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PropertyService propertyService;

    @MockitoSpyBean
    private PropertySearchRepository propertySearchRepositorySpy;

    @Test
    void filtersByDestinationPriceGuestsAndBedrooms() throws Exception {
        String city = uniqueCity();
        Property match = createActiveProperty(city, BigDecimal.valueOf(50000), 4, 2, Set.of("WIFI"));
        Property tooExpensive = createActiveProperty(city, BigDecimal.valueOf(200000), 4, 2, Set.of("WIFI"));
        Property tooFewBedrooms = createActiveProperty(city, BigDecimal.valueOf(50000), 4, 1, Set.of("WIFI"));
        Property wrongCity = createActiveProperty(uniqueCity(), BigDecimal.valueOf(50000), 4, 2, Set.of("WIFI"));

        mockMvc.perform(get("/api/v1/search")
                        .param("destination", city)
                        .param("minPrice", "10000")
                        .param("maxPrice", "100000")
                        .param("guests", "4")
                        .param("bedrooms", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + match.getId() + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + tooExpensive.getId() + "')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id=='" + tooFewBedrooms.getId() + "')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id=='" + wrongCity.getId() + "')]").doesNotExist());
    }

    @Test
    void amenityFilterRequiresAllSpecifiedAmenitiesNotJustAny() throws Exception {
        String city = uniqueCity();
        Property hasBoth = createActiveProperty(city, BigDecimal.valueOf(50000), 4, 2, Set.of("WIFI", "POOL"));
        Property hasOnlyWifi = createActiveProperty(city, BigDecimal.valueOf(50000), 4, 2, Set.of("WIFI"));

        mockMvc.perform(get("/api/v1/search")
                        .param("destination", city)
                        .param("amenities", "WIFI", "POOL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + hasBoth.getId() + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + hasOnlyWifi.getId() + "')]").doesNotExist());
    }

    @Test
    void excludesAPropertyBlockedForTheRequestedDateRangeButNotForAClearRange() throws Exception {
        String city = uniqueCity();
        Property property = createActiveProperty(city, BigDecimal.valueOf(50000), 4, 2, Set.of("WIFI"));
        propertyService.setAvailability(property.getHostId(), property.getId(), new SetAvailabilityRequest(
                List.of(new AvailabilityDayInput(LocalDate.of(2026, 9, 10), true, null))));

        mockMvc.perform(get("/api/v1/search")
                        .param("destination", city)
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-05"))
                .andExpect(jsonPath("$.data[?(@.id=='" + property.getId() + "')]").exists());

        mockMvc.perform(get("/api/v1/search")
                        .param("destination", city)
                        .param("checkIn", "2026-09-08")
                        .param("checkOut", "2026-09-12"))
                .andExpect(jsonPath("$.data[?(@.id=='" + property.getId() + "')]").doesNotExist());
    }

    @Test
    void rejectsACheckInWithoutACheckOut() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("checkIn", "2026-09-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", Matchers.equalTo("INCOMPLETE_DATE_RANGE")));
    }

    @Test
    void paginatesResults() throws Exception {
        String city = uniqueCity();
        for (int i = 0; i < 5; i++) {
            createActiveProperty(city, BigDecimal.valueOf(10000 + i), 2, 1, Set.of());
        }

        mockMvc.perform(get("/api/v1/search").param("destination", city).param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.total").value(5));
        mockMvc.perform(get("/api/v1/search").param("destination", city).param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void sortsByPriceAscendingWhenRequested() throws Exception {
        String city = uniqueCity();
        Property cheap = createActiveProperty(city, BigDecimal.valueOf(10000), 2, 1, Set.of());
        Property expensive = createActiveProperty(city, BigDecimal.valueOf(90000), 2, 1, Set.of());

        mockMvc.perform(get("/api/v1/search").param("destination", city).param("sort", "price_asc"))
                .andExpect(jsonPath("$.data[0].id").value(cheap.getId().toString()))
                .andExpect(jsonPath("$.data[1].id").value(expensive.getId().toString()));
    }

    @Test
    void draftListingsNeverAppearInSearchResults() throws Exception {
        String city = uniqueCity();
        UUID hostId = registerHostUserId();
        propertyService.create(hostId, propertyRequest(city, BigDecimal.valueOf(10000), 2, 1, Set.of())); // stays DRAFT

        mockMvc.perform(get("/api/v1/search").param("destination", city))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cachesIdenticalQueriesAndInvalidatesWhenAPropertyChanges() throws Exception {
        String city = uniqueCity();
        createActiveProperty(city, BigDecimal.valueOf(10000), 2, 1, Set.of());

        mockMvc.perform(get("/api/v1/search").param("destination", city))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(propertySearchRepositorySpy, times(1)).search(any(), any());

        // identical query again -> served from cache, no second repository call
        mockMvc.perform(get("/api/v1/search").param("destination", city))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(propertySearchRepositorySpy, times(1)).search(any(), any());

        // a change anywhere bumps the cache generation, so the next query re-queries...
        createActiveProperty(city, BigDecimal.valueOf(20000), 2, 1, Set.of());
        mockMvc.perform(get("/api/v1/search").param("destination", city))
                .andExpect(jsonPath("$.data.length()").value(2));
        verify(propertySearchRepositorySpy, times(2)).search(any(), any());
    }

    private Property createActiveProperty(String city, BigDecimal price, int capacity, int bedrooms, Set<String> amenityCodes)
            throws Exception {
        UUID hostId = registerHostUserId();
        Property created = propertyService.create(hostId, propertyRequest(city, price, capacity, bedrooms, amenityCodes));
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private CreatePropertyRequest propertyRequest(String city, BigDecimal price, int capacity, int bedrooms, Set<String> amenityCodes) {
        return new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", city, city, "Nigeria",
                null, null, null, price, capacity, bedrooms, bedrooms, BigDecimal.ONE, null, null, null, null, amenityCodes);
    }

    private UUID registerHostUserId() throws Exception {
        String email = "search-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Search Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.user().id();
    }

    private String uniqueCity() {
        return "City" + UUID.randomUUID().toString().substring(0, 8);
    }
}
