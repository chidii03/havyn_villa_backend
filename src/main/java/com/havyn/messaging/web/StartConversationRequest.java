package com.havyn.messaging.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StartConversationRequest(UUID bookingId, @NotBlank @Size(max = 4000) String body) {
}
