package com.havyn.messaging.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.messaging.domain.Conversation;
import com.havyn.messaging.service.ConversationService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not under {@code /api/v1/properties/**}, so it's authenticated by SecurityConfig's
 * default {@code anyRequest().authenticated()} fallback without needing any carve-out —
 * only {@link PropertyConversationController}'s nested creation endpoint needed one.
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final PropertyRepository propertyRepository;

    public ConversationController(ConversationService conversationService, PropertyRepository propertyRepository) {
        this.conversationService = conversationService;
        this.propertyRepository = propertyRepository;
    }

    @GetMapping
    public PageResponse<ConversationSummary> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(conversationService.listForUser(principal(authentication), pageable).map(this::toSummary));
    }

    @GetMapping("/{id}")
    public ConversationSummary get(Authentication authentication, @PathVariable UUID id) {
        return toSummary(conversationService.getParticipant(principal(authentication), id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageSummary> sendMessage(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        MessageSummary summary = MessageSummary.from(conversationService.sendMessage(principal(authentication), id, request.body()));
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageSummary> listMessages(
            Authentication authentication, @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(conversationService.listMessages(principal(authentication), id, pageable).map(MessageSummary::from));
    }

    @PostMapping("/{id}/read")
    public void markRead(Authentication authentication, @PathVariable UUID id) {
        conversationService.markRead(principal(authentication), id);
    }

    private ConversationSummary toSummary(Conversation conversation) {
        String propertyTitle = propertyRepository.findById(conversation.getPropertyId())
                .map(Property::getTitle)
                .orElse("Listing no longer available");
        return ConversationSummary.from(conversation, propertyTitle);
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
