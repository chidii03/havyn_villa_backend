package com.havyn.hosts.web;

import com.havyn.auth.domain.AuthResult;
import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.auth.web.AuthController;
import com.havyn.auth.web.AuthResponse;
import com.havyn.auth.web.UserSummary;
import com.havyn.common.reference.Role;
import com.havyn.hosts.service.HostOnboardingService;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-serve host onboarding — see project-docs/prompts/17-host-dashboard.md. Any
 * authenticated user may call this (not {@code @PreAuthorize("hasRole('HOST')")} —
 * that would be circular); response/cookie shape deliberately mirrors
 * {@code AuthController#register}/{@code #login} exactly (same {@link AuthResponse},
 * same {@value AuthController#REFRESH_COOKIE_NAME} cookie) so the frontend's existing
 * session-handling code applies the result the same way a login response would.
 */
@RestController
@RequestMapping("/api/v1/host/onboarding")
public class HostOnboardingController {

    private final HostOnboardingService hostOnboardingService;
    private final ProfileRepository profileRepository;
    private final Duration refreshTtl;

    public HostOnboardingController(
            HostOnboardingService hostOnboardingService,
            ProfileRepository profileRepository,
            @Value("${havyn.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.hostOnboardingService = hostOnboardingService;
        this.profileRepository = profileRepository;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    @PostMapping
    public AuthResponse becomeHost(Authentication authentication, HttpServletResponse response) {
        UUID userId = ((AuthenticatedUser) authentication.getPrincipal()).userId();
        AuthResult result = hostOnboardingService.becomeHost(userId);
        setRefreshCookie(response, result.refreshToken());
        return AuthResponse.bearer(result.accessToken(), result.refreshToken(), result.expiresInSeconds(), toSummary(result.user()));
    }

    private UserSummary toSummary(User user) {
        Profile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet()),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getPhone() : null,
                profile != null ? profile.getAvatarUrl() : null);
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(AuthController.REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(refreshTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
