package com.havyn.auth.web;

import java.util.Set;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        boolean emailVerified,
        Set<String> roles,
        String fullName,
        String phone,
        String avatarUrl) {
}
