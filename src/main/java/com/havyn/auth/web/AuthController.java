package com.havyn.auth.web;

import com.havyn.auth.domain.AuthResult;
import com.havyn.auth.domain.AuthService;
import com.havyn.auth.domain.GoogleTokenVerifier;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.reference.Role;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register/login/refresh/logout, email verification, password reset — see
 * project-docs/architecture/03-api-design.md and project-docs/prompts/09-authentication.md.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "havyn_refresh";

    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final ProfileRepository profileRepository;
    private final Duration refreshTtl;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;

    public AuthController(
            AuthService authService,
            GoogleTokenVerifier googleTokenVerifier,
            ProfileRepository profileRepository,
            @Value("${havyn.jwt.refresh-ttl-days}") long refreshTtlDays,
            @Value("${havyn.auth.cookie.secure:false}") boolean refreshCookieSecure,
            @Value("${havyn.auth.cookie.same-site:Lax}") String refreshCookieSameSite) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.profileRepository = profileRepository;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResult result = authService.register(request.email(), request.password(), request.fullName());
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request.email(), request.password());
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.loginWithGoogle(googleTokenVerifier.verify(request.idToken()));
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body,
            HttpServletResponse response) {
        String token = resolveToken(cookieToken, body);
        AuthResult result = authService.refresh(token);
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body,
            HttpServletResponse response) {
        String token = cookieToken != null ? cookieToken : (body != null ? body.refreshToken() : null);
        if (token != null) {
            authService.logout(token);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    private String resolveToken(String cookieToken, RefreshRequest body) {
        String token = cookieToken != null ? cookieToken : (body != null ? body.refreshToken() : null);
        if (token == null || token.isBlank()) {
            throw new BadRequestException("MISSING_REFRESH_TOKEN", "No refresh token provided");
        }
        return token;
    }

    private AuthResponse toResponse(AuthResult result) {
        return AuthResponse.bearer(
                result.accessToken(), result.refreshToken(), result.expiresInSeconds(), toSummary(result.user()));
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
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(token, refreshTtl).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
