package com.havyn.support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.domain.SupportChatRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OpenAiSupportClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSupportClient.class);
    private static final String SYSTEM_PROMPT = """
            You are Havyn Villa's AI Assistant. Help users book, manage trips, use the platform,
            and resolve complaints about bookings, payments, hosting, cancellations, and refunds.
            Ask only for information you need, such as booking reference, dates, and issue details.
            If a user reports a problem, summarize it clearly and tell them it has been flagged for Admin.
            Explain how to navigate search, filters, bookings, wishlist, becoming a host, managing listings,
            cancellations, refunds, trips, and support. Trips is where guests review bookings. Hosts manage listings
            from Host dashboard > Listings. Payments use secure hosted checkout, with Paystack configured by default
            and Flutterwave available as a provider. Be professional, concise, and never share other users' data.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiSupportClient(
            RestClient.Builder restClientBuilder,
            @Value("${havyn.openai.api-key:}") String apiKey,
            @Value("${havyn.openai.model:gpt-5-mini}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClientBuilder.baseUrl("https://api.openai.com/v1").build();
    }

    public String respond(List<SupportChatMessage> history) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceUnavailableException("OPENAI_NOT_CONFIGURED", "AI support chat is not configured yet");
        }

        String input = inputFrom(history);

        JsonNode response;
        String clientRequestId = UUID.randomUUID().toString();
        try {
            response = restClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Client-Request-Id", clientRequestId)
                    .body(Map.of("model", model, "instructions", SYSTEM_PROMPT, "input", input, "store", false))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String code = errorCode(e);
            log.warn(
                    "OpenAI support request failed code={} status={} clientRequestId={} body={}",
                    code,
                    e.getStatusCode().value(),
                    clientRequestId,
                    e.getResponseBodyAsString());
            throw new ServiceUnavailableException(code, userMessage(code));
        } catch (ResourceAccessException e) {
            log.warn("OpenAI support request failed code=OPENAI_NETWORK_ERROR clientRequestId={} message={}",
                    clientRequestId, e.getMessage());
            throw new ServiceUnavailableException("OPENAI_NETWORK_ERROR", "AI support chat could not reach the AI service");
        } catch (RestClientException e) {
            log.warn("OpenAI support request failed code=OPENAI_CLIENT_ERROR clientRequestId={} message={}",
                    clientRequestId, e.getMessage());
            throw new ServiceUnavailableException("OPENAI_CLIENT_ERROR", "AI support chat is temporarily unavailable");
        }

        String content = extractOutputText(response);
        if (content.isBlank()) {
            log.warn("OpenAI support request returned empty content clientRequestId={} response={}", clientRequestId, response);
            throw new ServiceUnavailableException("OPENAI_EMPTY_RESPONSE", "AI support chat returned an empty response");
        }
        return content.trim();
    }

    private String errorCode(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString().toLowerCase();
        if (status == 401 || status == 403 || body.contains("invalid_api_key")) {
            return "OPENAI_AUTH_FAILED";
        }
        if (status == 429 && (body.contains("insufficient_quota") || body.contains("credit_balance_exhausted"))) {
            return "OPENAI_QUOTA_EXCEEDED";
        }
        if (status == 429) {
            return "OPENAI_RATE_LIMITED";
        }
        if (status >= 500) {
            return "OPENAI_SERVER_ERROR";
        }
        return "OPENAI_REQUEST_FAILED";
    }

    private String userMessage(String code) {
        return switch (code) {
            case "OPENAI_AUTH_FAILED" -> "AI support chat is not authenticated with the AI provider";
            case "OPENAI_QUOTA_EXCEEDED" -> "AI support chat has no available AI credits right now";
            case "OPENAI_RATE_LIMITED" -> "AI support chat is receiving too many requests right now";
            case "OPENAI_SERVER_ERROR" -> "The AI provider is temporarily unavailable";
            default -> "AI support chat is temporarily unavailable";
        };
    }

    private String inputFrom(List<SupportChatMessage> history) {
        StringBuilder builder = new StringBuilder("Recent conversation:\n");
        history.stream().skip(Math.max(0, history.size() - 20)).forEach(message -> {
            String role = message.getRole() == SupportChatRole.USER ? "User" : "Assistant";
            builder.append(role).append(": ").append(message.getBody()).append('\n');
        });
        builder.append("\nReply as Havyn Villa's AI Assistant to the latest user message.");
        return builder.toString();
    }

    private String extractOutputText(JsonNode response) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode outputItem : response.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                if ("output_text".equals(contentItem.path("type").asText()) || contentItem.hasNonNull("text")) {
                    String text = contentItem.path("text").asText("");
                    if (!text.isBlank()) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(text);
                    }
                }
            }
        }
        return builder.toString();
    }
}
