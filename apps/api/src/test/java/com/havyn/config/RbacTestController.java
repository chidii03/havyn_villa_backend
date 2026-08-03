package com.havyn.config;

import com.havyn.auth.domain.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only (src/test, never shipped) — demonstrates and exercises the two RBAC
 * patterns future modules reuse: role-based ({@code hasRole}) and object-level/IDOR
 * (comparing a path variable to {@code authentication.principal.userId}).
 */
@RestController
@RequestMapping("/__test/rbac")
class RbacTestController {

    @GetMapping("/authenticated-only")
    String authenticatedOnly() {
        return "ok";
    }

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "ok";
    }

    @GetMapping("/users/{userId}/own-resource")
    @PreAuthorize("#userId == authentication.principal.userId")
    String ownResourceOnly(@PathVariable UUID userId, Authentication authentication) {
        return "ok:" + ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
