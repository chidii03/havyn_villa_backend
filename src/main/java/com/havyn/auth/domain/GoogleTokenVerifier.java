package com.havyn.auth.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.havyn.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleTokenVerifier {

    private final RestClient restClient;
    private final String clientId;

    public GoogleTokenVerifier(RestClient.Builder restClientBuilder, @Value("${havyn.google.client-id:}") String clientId) {
        this.restClient = restClientBuilder.baseUrl("https://oauth2.googleapis.com").build();
        this.clientId = clientId;
    }

    public GoogleUser verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new BadRequestException("GOOGLE_AUTH_NOT_CONFIGURED", "Google sign-in is not configured yet");
        }

        JsonNode tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new BadRequestException("INVALID_GOOGLE_TOKEN", "Google sign-in could not be verified");
        }

        if (tokenInfo == null || !clientId.equals(tokenInfo.path("aud").asText())) {
            throw new BadRequestException("INVALID_GOOGLE_TOKEN", "Google sign-in could not be verified");
        }
        if (!tokenInfo.path("email_verified").asBoolean(false)) {
            throw new BadRequestException("GOOGLE_EMAIL_NOT_VERIFIED", "Google account email is not verified");
        }

        String email = tokenInfo.path("email").asText("");
        if (email.isBlank()) {
            throw new BadRequestException("GOOGLE_EMAIL_MISSING", "Google did not return an email address");
        }

        return new GoogleUser(
                email,
                tokenInfo.path("name").asText(email),
                tokenInfo.path("picture").asText(null));
    }

    public record GoogleUser(String email, String fullName, String pictureUrl) {
    }
}
