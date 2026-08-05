package com.havyn.auth.domain;

import java.util.Set;
import java.util.UUID;

/**
 * The Spring Security {@code Authentication} principal set by the JWT filter —
 * everything an {@code @PreAuthorize} SpEL expression needs (e.g.
 * {@code #hostId == authentication.principal.userId} for object-level/IDOR checks)
 * without a DB round trip per request.
 */
public record AuthenticatedUser(UUID userId, String email, Set<String> roles) {
}
