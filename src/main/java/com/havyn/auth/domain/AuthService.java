package com.havyn.auth.domain;

import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import com.havyn.users.repo.UserRepository;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the auth lifecycle. Business rules live here, not in the controller —
 * see project-docs/backend/02-domain-modules.md#auth.
 *
 * <p>Logs security-relevant events (login success/failure, registration, logout,
 * password reset) at INFO/WARN — an interim, log-based audit trail ahead of the real
 * queryable {@code AuditLog} table, which is prompt 18's job (see
 * project-docs/security/01-security-plan.md's prompt 24 notes). Never logs a raw
 * password, token, or other credential — user id where a user is already known
 * (login success, registration, logout, verification, password reset all log this,
 * not an email); the one case with no user id yet (a failed login attempt, where the
 * account may not even exist) logs a truncated SHA-256 hash of the attempted email
 * instead of the raw address (prompt 26's "no PII/secrets in logs") — still lets ops
 * correlate repeated attempts against the same address without the log itself
 * containing a directly re-identifying value.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE_CODE = "CUSTOMER";
    private static final String GOOGLE_PASSWORD_PLACEHOLDER = "GOOGLE_ACCOUNT_NO_PASSWORD_LOGIN";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final Mailer mailer;

    public AuthService(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            VerificationTokenService verificationTokenService,
            Mailer mailer) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.mailer = mailer;
    }

    @Transactional
    public AuthResult register(String email, String rawPassword, String fullName) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "That email is already registered");
        }

        Role customerRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException(DEFAULT_ROLE_CODE + " role must be seeded by V1__init.sql"));

        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword));
        user.addRole(customerRole);
        user = userRepository.save(user);
        profileRepository.save(new Profile(user, fullName.trim()));

        sendEmailVerification(user);

        log.info("Registered user userId={}", user.getId());
        return issueTokens(user);
    }

    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Deliberately still logs a value derived from the attempted email — the
            // *response* to the caller stays generic (no enumeration, see
            // InvalidCredentialsException's callers), and ops/security still needs to
            // be able to spot the same address being hammered repeatedly — but a
            // hash, not the raw address, keeps the log itself free of PII.
            log.warn("Failed login attempt emailHash={}", shortHash(normalizedEmail));
            throw new InvalidCredentialsException();
        }
        log.info("Login succeeded userId={}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult loginWithGoogle(GoogleTokenVerifier.GoogleUser googleUser) {
        String normalizedEmail = googleUser.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            Role customerRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                    .orElseThrow(() -> new IllegalStateException(DEFAULT_ROLE_CODE + " role must be seeded by V1__init.sql"));
            user = new User(normalizedEmail, GOOGLE_PASSWORD_PLACEHOLDER);
            user.addRole(customerRole);
            user.markEmailVerified(Instant.now());
            user = userRepository.save(user);

            Profile profile = new Profile(user, safeFullName(googleUser));
            profile.setAvatarUrl(googleUser.pictureUrl());
            profileRepository.save(profile);
            log.info("Registered Google user userId={}", user.getId());
        } else if (!user.isEmailVerified()) {
            user.markEmailVerified(Instant.now());
        }

        log.info("Google login succeeded userId={}", user.getId());
        return issueTokens(user);
    }

    /** First 12 hex chars of SHA-256 — enough to correlate repeated attempts, not to reverse the input. */
    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Issued rotated = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> InvalidRefreshTokenException.expiredOrRevoked());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roleCodes(user));
        return new AuthResult(accessToken, rotated.token(), jwtService.accessTtl().toSeconds(), user);
    }

    /**
     * Re-mints access+refresh tokens for an already-loaded, already-mutated user —
     * e.g. {@code hosts/} calling this right after granting the HOST role, so the
     * caller gets a token reflecting it immediately, without a full re-login. Public
     * (unlike the private {@link #issueTokens}) specifically for this cross-module use.
     */
    public AuthResult reissueTokens(User user) {
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        try {
            JwtService.RefreshClaims claims = jwtService.parseRefreshToken(refreshToken);
            refreshTokenService.revokeFamily(claims.familyId());
            log.info("Logout userId={}", claims.userId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Already invalid/expired/malformed — nothing to revoke. Logout is idempotent.
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = verificationTokenService.consumeEmailVerificationToken(rawToken)
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_TOKEN", "This verification link is invalid or has expired"));
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        user.markEmailVerified(Instant.now());
        log.info("Email verified userId={}", userId);
    }

    public void sendEmailVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        String verificationToken = verificationTokenService.issueEmailVerificationToken(user.getId());
        mailer.sendEmailVerification(user.getEmail(), verificationToken);
        log.info("Email verification requested userId={}", user.getId());
    }

    /** Always completes normally regardless of whether the email exists — no account enumeration. */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String token = verificationTokenService.issuePasswordResetToken(user.getId());
            mailer.sendPasswordReset(user.getEmail(), token);
            // Only logged when the account actually exists — the response to the caller
            // stays identical either way (no enumeration), but this log line itself is
            // server-side only, never observable by the requester.
            log.info("Password reset requested userId={}", user.getId());
        });
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        UUID userId = verificationTokenService.consumePasswordResetToken(rawToken)
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_TOKEN", "This reset link is invalid or has expired"));
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Password changed — every existing session everywhere is revoked, not just this device.
        refreshTokenService.revokeAllForUser(userId);
        log.info("Password reset confirmed, all sessions revoked userId={}", userId);
    }

    private AuthResult issueTokens(User user) {
        Set<String> roles = roleCodes(user);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), roles);
        RefreshTokenService.Issued issued = refreshTokenService.issue(user.getId());
        return new AuthResult(accessToken, issued.token(), jwtService.accessTtl().toSeconds(), user);
    }

    private String safeFullName(GoogleTokenVerifier.GoogleUser googleUser) {
        String fullName = googleUser.fullName();
        if (fullName == null || fullName.isBlank()) {
            return googleUser.email();
        }
        return fullName.trim();
    }

    private Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
    }
}
