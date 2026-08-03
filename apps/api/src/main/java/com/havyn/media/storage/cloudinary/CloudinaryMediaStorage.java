package com.havyn.media.storage.cloudinary;

import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.storage.MediaStorage;
import com.havyn.media.storage.SignedUpload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Real Cloudinary signed-upload integration (https://cloudinary.com/documentation/upload_images#generating_authentication_signatures).
 * {@code createSignedUpload} is pure local computation — no network call, since the
 * actual file upload happens directly from the client's browser to Cloudinary using
 * this signature, never through our backend. {@code deleteAsset} does call Cloudinary
 * (the "destroy" API) and shares the same live-credential gap as {@code
 * PaystackPaymentProvider} — no live Cloudinary account exists in this environment.
 * Signature generation is real and unit-tested against independently-computed SHA-1
 * values.
 */
@Component
public class CloudinaryMediaStorage implements MediaStorage {

    private final RestClient restClient;
    private final CloudinaryProperties properties;
    private final Clock clock;

    public CloudinaryMediaStorage(RestClient.Builder restClientBuilder, CloudinaryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.restClient = restClientBuilder.baseUrl("https://api.cloudinary.com").build();
    }

    @Override
    public SignedUpload createSignedUpload(String folder) {
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> paramsToSign = new LinkedHashMap<>();
        paramsToSign.put("folder", folder);
        paramsToSign.put("timestamp", String.valueOf(timestamp));

        String signature = sign(paramsToSign);
        return new SignedUpload(properties.getCloudName(), properties.getApiKey(), timestamp, signature, folder);
    }

    @Override
    public boolean isValidAssetUrl(String secureUrl) {
        return secureUrl != null && secureUrl.contains("res.cloudinary.com/" + properties.getCloudName() + "/");
    }

    @Override
    public void deleteAsset(String publicId, MediaResourceType resourceType) {
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> paramsToSign = new LinkedHashMap<>();
        paramsToSign.put("public_id", publicId);
        paramsToSign.put("timestamp", String.valueOf(timestamp));
        String signature = sign(paramsToSign);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("public_id", publicId);
        body.add("api_key", properties.getApiKey());
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);

        restClient.post()
                .uri("/v1_1/{cloud}/{type}/destroy", properties.getCloudName(), resourceType.name().toLowerCase())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Cloudinary's documented algorithm: sort params alphabetically, join as key=value&..., append the raw API secret (no separator), SHA-1, hex-encode. */
    private String sign(Map<String, String> params) {
        String paramString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return sha1Hex(paramString + properties.getApiSecret());
    }

    private static String sha1Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute SHA-1 signature", e);
        }
    }
}
