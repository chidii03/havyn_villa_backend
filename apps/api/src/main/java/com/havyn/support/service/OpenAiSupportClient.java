package com.havyn.support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.support.domain.SupportChatMessage;
import com.havyn.support.domain.SupportChatRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiSupportClient {

    private static final String SYSTEM_PROMPT = """
            You are Havyn Villa's AI Assistant. Help guests and hosts with bookings, properties,
            cancellations, payments, wishlists, account questions, and general support. Be concise,
            friendly, and practical. If a request needs a human or private account action you cannot
            perform, say so clearly and suggest the next step.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiSupportClient(
            RestClient.Builder restClientBuilder,
            @Value("${havyn.openai.api-key:}") String apiKey,
            @Value("${havyn.openai.model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClientBuilder.baseUrl("https://api.openai.com/v1").build();
    }

    public String respond(List<SupportChatMessage> history) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceUnavailableException("OPENAI_NOT_CONFIGURED", "AI support chat is not configured yet");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        history.stream().skip(Math.max(0, history.size() - 20)).forEach(message -> messages.add(Map.of(
                "role", message.getRole() == SupportChatRole.USER ? "user" : "assistant",
                "content", message.getBody())));

        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "messages", messages, "temperature", 0.4))
                .retrieve()
                .body(JsonNode.class);

        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new ServiceUnavailableException("OPENAI_EMPTY_RESPONSE", "AI support chat returned an empty response");
        }
        return content.trim();
    }
}
