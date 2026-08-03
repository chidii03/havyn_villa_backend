package com.havyn.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import com.havyn.users.repo.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * project-docs/prompts/26-observability.md: "log-scrubbing test (no PII)." Only
 * {@code login()}'s failure path is exercised here — the other AuthService methods
 * already have real, deep coverage via AuthFlowIT (Testcontainers); this test exists
 * specifically to pin the log-scrubbing behavior, which AuthFlowIT has no way to
 * assert on (it drives the real HTTP API, not the logger).
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
    private final Mailer mailer = mock(Mailer.class);

    private final AuthService service = new AuthService(
            userRepository, profileRepository, roleRepository, passwordEncoder, jwtService, refreshTokenService,
            verificationTokenService, mailer);

    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void captureLogs() {
        logCapture = new ListAppender<>();
        logCapture.start();
        ((Logger) LoggerFactory.getLogger(AuthService.class)).addAppender(logCapture);
    }

    @AfterEach
    void stopCapture() {
        ((Logger) LoggerFactory.getLogger(AuthService.class)).detachAppender(logCapture);
    }

    @Test
    void failedLogin_logsAHashOfTheEmail_neverTheRawAddress() {
        String email = "someone-real@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(email, "wrong-password")).isInstanceOf(InvalidCredentialsException.class);

        String logged = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertThat(logged).doesNotContain(email);
        assertThat(logged).doesNotContain("someone-real");
        assertThat(logged).containsPattern("emailHash=[0-9a-f]{12}");
    }

    @Test
    void failedLogin_theSameEmailAlwaysHashesTheSameWay_soOpsCanStillCorrelateRepeatedAttempts() {
        String email = "targeted@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(email, "wrong-1")).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(email, "wrong-2")).isInstanceOf(InvalidCredentialsException.class);

        java.util.List<String> messages = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo(messages.get(1));
    }

    @Test
    void successfulLogin_logsTheUserIdNotTheEmail() {
        String email = "ada@example.com";
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getRoles()).thenReturn(java.util.Set.of());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("correct-password"), any())).thenReturn(true);
        when(refreshTokenService.issue(eq(userId))).thenReturn(new RefreshTokenService.Issued("token", userId, familyId));

        service.login(email, "correct-password");

        String logged = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertThat(logged).doesNotContain(email);
        assertThat(logged).contains(userId.toString());
    }
}
