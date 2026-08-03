package com.havyn.admin.web;

import com.havyn.common.reference.Role;
import com.havyn.users.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AdminUserSummary(
        UUID id, String email, boolean emailVerified, Set<String> roles, String status, String fullName, Instant createdAt) {

    public static AdminUserSummary from(User user, String fullName) {
        return new AdminUserSummary(
                user.getId(), user.getEmail(), user.isEmailVerified(),
                user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet()),
                user.getStatus().name(), fullName, user.getCreatedAt());
    }
}
