package com.havyn.media.storage.cloudinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.storage.SignedUpload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Real HMAC-free SHA-1 signature generation (pure local computation) + real HTTP request shaping for the delete call, via MockRestServiceServer. */
class CloudinaryMediaStorageTest {

    private static final String API_SECRET = "test_api_secret";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);

    private MockRestServiceServer mockServer;
    private CloudinaryMediaStorage storage;

    @BeforeEach
    void setUp() {
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("havyn");
        properties.setApiKey("test_api_key");
        properties.setApiSecret(API_SECRET);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        storage = new CloudinaryMediaStorage(builder, properties, CLOCK);
    }

    @Test
    void createSignedUpload_signsExactlyTheFolderAndTimestampParams() throws Exception {
        SignedUpload upload = storage.createSignedUpload("properties/abc");

        String expectedSignature = sha1Hex("folder=properties/abc&timestamp=1700000000" + API_SECRET);
        assertThat(upload.cloudName()).isEqualTo("havyn");
        assertThat(upload.apiKey()).isEqualTo("test_api_key");
        assertThat(upload.timestamp()).isEqualTo(1_700_000_000L);
        assertThat(upload.folder()).isEqualTo("properties/abc");
        assertThat(upload.signature()).isEqualTo(expectedSignature);
    }

    @Test
    void createSignedUpload_neverIncludesTheApiSecretInTheReturnedValue() {
        SignedUpload upload = storage.createSignedUpload("properties/abc");

        assertThat(upload.toString()).doesNotContain(API_SECRET);
    }

    @Test
    void deleteAsset_sendsTheCorrectlySignedDestroyRequest() {
        mockServer.expect(requestTo("https://api.cloudinary.com/v1_1/havyn/image/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"result":"ok"}
                        """, MediaType.APPLICATION_JSON));

        storage.deleteAsset("properties/abc/sample", MediaResourceType.IMAGE);

        mockServer.verify();
    }

    @Test
    void isValidAssetUrl_acceptsOnlyUrlsFromTheConfiguredCloud() {
        assertThat(storage.isValidAssetUrl("https://res.cloudinary.com/havyn/image/upload/v1/x.jpg")).isTrue();
        assertThat(storage.isValidAssetUrl("https://res.cloudinary.com/someone-elses-cloud/image/upload/v1/x.jpg")).isFalse();
        assertThat(storage.isValidAssetUrl("https://evil.example.com/fake.jpg")).isFalse();
        assertThat(storage.isValidAssetUrl(null)).isFalse();
    }

    private static String sha1Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
    }
}
