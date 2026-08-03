package com.havyn.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.JwtService;
import com.havyn.auth.web.AuthResponse;
import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.storage.MediaStorage;
import com.havyn.media.storage.SignedUpload;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.CreatePropertyRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Signature -> persist -> reorder -> delete, authz, and reject-bad-upload — see
 * project-docs/prompts/14-media-storage.md's acceptance criteria. {@link
 * MediaStorage} is mocked (not the real {@code CloudinaryMediaStorage}) — no live
 * Cloudinary account exists in this environment; see {@code CloudinaryMediaStorage}'s
 * own Javadoc and unit tests for what's verified about the real adapter.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MediaFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PropertyService propertyService;

    @MockitoBean
    private MediaStorage mediaStorage;

    @BeforeEach
    void setUp() {
        when(mediaStorage.isValidAssetUrl(any())).thenReturn(true);
    }

    @Test
    void hostCanSignPersistReorderAndDeleteMedia_publicSeesItOnceActive_nonOwnerIsBlocked() throws Exception {
        UUID hostId = registerUserId();
        Property property = createActiveProperty(hostId);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        String mediaBase = "/api/v1/host/listings/" + property.getId() + "/media";

        when(mediaStorage.createSignedUpload(any()))
                .thenReturn(new SignedUpload("havyn", "test-api-key", 1_700_000_000L, "sig-abc", "properties/" + property.getId()));

        MvcResult signatureResult = mockMvc.perform(post(mediaBase + "/signature").header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature", equalTo("sig-abc")))
                .andExpect(jsonPath("$.cloudName", equalTo("havyn")))
                .andReturn();
        // No secret anywhere in what the browser receives.
        assertThat(signatureResult.getResponse().getContentAsString().toLowerCase()).doesNotContain("secret");

        String firstId = addMedia(mediaBase, hostToken, "public-1", "jpg", MediaResourceType.IMAGE, 1000);
        String secondId = addMedia(mediaBase, hostToken, "public-2", "mp4", MediaResourceType.VIDEO, 2000);

        mockMvc.perform(get(mediaBase).header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].posterUrl").exists()); // the video gets a poster URL, the image doesn't

        // Public can see the same media once the listing is ACTIVE (createActiveProperty already published it).
        mockMvc.perform(get("/api/v1/properties/" + property.getId() + "/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Reorder: second first, first second.
        mockMvc.perform(put(mediaBase + "/order")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedMediaIds\":[\"" + secondId + "\",\"" + firstId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", equalTo(secondId)))
                .andExpect(jsonPath("$[1].id", equalTo(firstId)));

        // A different host can't see, reorder, or delete this listing's media.
        String otherHostToken = jwtService.issueAccessToken(registerUserId(), "unused2@example.com", Set.of("CUSTOMER", "HOST"));
        mockMvc.perform(get(mediaBase).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHostToken)).andExpect(status().isForbidden());
        mockMvc.perform(delete(mediaBase + "/" + firstId).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherHostToken))
                .andExpect(status().isForbidden());

        // Delete removes both the DB row and the Cloudinary asset.
        mockMvc.perform(delete(mediaBase + "/" + firstId).header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isNoContent());
        verify(mediaStorage).deleteAsset(eq("public-1"), eq(MediaResourceType.IMAGE));

        mockMvc.perform(get(mediaBase).header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rejectsAnUnsupportedFormatAndAnOversizedFile() throws Exception {
        UUID hostId = registerUserId();
        Property property = createActiveProperty(hostId);
        String hostToken = jwtService.issueAccessToken(hostId, "unused@example.com", Set.of("CUSTOMER", "HOST"));
        String mediaBase = "/api/v1/host/listings/" + property.getId() + "/media";

        mockMvc.perform(post(mediaBase)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMediaBody("public-bmp", "bmp", MediaResourceType.IMAGE, 1000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("UNSUPPORTED_MEDIA_FORMAT")));

        mockMvc.perform(post(mediaBase)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMediaBody("public-huge", "jpg", MediaResourceType.IMAGE, 999_999_999_999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("MEDIA_TOO_LARGE")));
        verify(mediaStorage).deleteAsset(eq("public-huge"), eq(MediaResourceType.IMAGE));
    }

    private String addMedia(String mediaBase, String hostToken, String publicId, String format, MediaResourceType type, long bytes)
            throws Exception {
        MvcResult result = mockMvc.perform(post(mediaBase)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMediaBody(publicId, format, type, bytes)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String addMediaBody(String publicId, String format, MediaResourceType type, long bytes) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "publicId", publicId,
                "secureUrl", "https://res.cloudinary.com/havyn/" + type.name().toLowerCase() + "/upload/v1/" + publicId + "." + format,
                "resourceType", type.name(),
                "format", format,
                "width", 800,
                "height", 600,
                "bytes", bytes));
    }

    private Property createActiveProperty(UUID hostId) throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Listing " + UUID.randomUUID(), "A lovely place to stay.", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", null, null, null, BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.ONE, null, null, null, null, Set.of());
        Property created = propertyService.create(hostId, request);
        propertyService.transition(hostId, created.getId(), PropertyStatus.PENDING);
        return propertyService.transition(hostId, created.getId(), PropertyStatus.ACTIVE);
    }

    private UUID registerUserId() throws Exception {
        String email = "media-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "correct-horse-battery-staple", "fullName", "Media Host"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.user().id();
    }
}
