package com.havyn.messaging.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.messaging.domain.Conversation;
import com.havyn.messaging.service.ConversationService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starting a conversation is nested under a property, mirroring {@code ReviewController}
 * (prompt 15) — see SecurityConfig's explicit POST-only carve-out from the
 * {@code /api/v1/properties/**} public wildcard. Everything else (list/get/send/read)
 * lives on {@link ConversationController}, since those aren't property-scoped.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/conversations")
public class PropertyConversationController {

    private final ConversationService conversationService;
    private final PropertyRepository propertyRepository;

    public PropertyConversationController(ConversationService conversationService, PropertyRepository propertyRepository) {
        this.conversationService = conversationService;
        this.propertyRepository = propertyRepository;
    }

    @PostMapping
    public ResponseEntity<ConversationSummary> start(
            Authentication authentication, @PathVariable UUID propertyId, @Valid @RequestBody StartConversationRequest request) {
        UUID guestId = principal(authentication);
        Conversation conversation = conversationService.startOrContinue(guestId, propertyId, request.bookingId(), request.body());
        String propertyTitle = propertyRepository.findById(propertyId).map(Property::getTitle).orElse("Listing no longer available");
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationSummary.from(conversation, propertyTitle));
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
