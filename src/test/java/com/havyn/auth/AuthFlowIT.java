package com.havyn.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.TestcontainersConfiguration;
import com.havyn.auth.domain.Mailer;
import com.havyn.auth.web.AuthResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full register -> verify -> login -> refresh (rotation + reuse detection) -> logout
 * lifecycle, plus account-enumeration and password-reset-revokes-sessions checks.
 * Requires Docker (Postgres + Redis). {@link Mailer} is mocked so no real SMTP/Mailhog
 * is needed to run this — the raw tokens are captured straight from the mock instead
 * of round-tripping through email.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private Mailer mailer;

    @Test
    void fullLifecycle_registerVerifyLoginRefreshRotationReuseDetectionLogout() throws Exception {
        String email = uniqueEmail();
        String password = "correct-horse-battery-staple";

        // --- register ---
        AuthResponse registerResponse = register(email, password, "Ada Lovelace");
        assertThat(registerResponse.user().emailVerified()).isFalse();
        assertThat(registerResponse.user().roles()).containsExactly("CUSTOMER");

        // --- verify email (token captured from the mocked Mailer, not a real inbox) ---
        ArgumentCaptor<String> verifyTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendEmailVerification(eq(email), verifyTokenCaptor.capture());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + verifyTokenCaptor.getValue() + "\"}"))
                .andExpect(status().isOk());

        // --- login (separate session/family from registration's) ---
        AuthResponse loginResponse = login(email, password);
        assertThat(loginResponse.refreshToken()).isNotEqualTo(registerResponse.refreshToken());

        // --- GET /me reflects verified status ---
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo(email)))
                .andExpect(jsonPath("$.emailVerified", equalTo(true)))
                .andExpect(jsonPath("$.fullName", equalTo("Ada Lovelace")));

        // --- refresh rotates the token ---
        AuthResponse rotated = refresh(loginResponse.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(loginResponse.refreshToken());

        // --- reusing the already-rotated (old) token is rejected... ---
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + loginResponse.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", equalTo("REFRESH_TOKEN_REUSE_DETECTED")));

        // --- ...and revokes the family: even the legitimately-rotated token now fails ---
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());

        // --- the original registration session is untouched by the above and can still log out ---
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + registerResponse.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + registerResponse.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSetsAnHttpOnlySecureRefreshCookie() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery-staple", "Grace Hopper");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "correct-horse-battery-staple")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        allOf(containsString("HttpOnly"), containsString("Secure"), containsString("havyn_refresh="))));
    }

    @Test
    void loginDoesNotLeakWhetherTheAccountExists() throws Exception {
        String registeredEmail = uniqueEmail();
        register(registeredEmail, "correct-horse-battery-staple", "Katherine Johnson");

        MvcResult nonexistentUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail(), "whatever-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(registeredEmail, "the-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(nonexistentUser.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse-battery-staple", "Margaret Hamilton");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "another-password-123", "Someone Else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", equalTo("EMAIL_ALREADY_REGISTERED")));
    }

    @Test
    void passwordResetRevokesEveryExistingSession() throws Exception {
        String email = uniqueEmail();
        String oldPassword = "correct-horse-battery-staple";
        AuthResponse session = register(email, oldPassword, "Radia Perlman");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> resetTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendPasswordReset(eq(email), resetTokenCaptor.capture());

        String newPassword = "a-brand-new-password-456";
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetTokenCaptor.getValue() + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        // The session opened before the reset is dead...
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());

        // ...the old password no longer works...
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, oldPassword)))
                .andExpect(status().isUnauthorized());

        // ...and the new one does.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, newPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRequestForAnUnknownEmailStillReturns202AndSendsNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\"}"))
                .andExpect(status().isAccepted());

        verify(mailer, never()).sendPasswordReset(anyString(), anyString());
    }

    private AuthResponse register(String email, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password, fullName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private AuthResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private AuthResponse refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String registerBody(String email, String password, String fullName) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password, "fullName", fullName));
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String uniqueEmail() {
        return "auth-flow-it-" + UUID.randomUUID() + "@example.com";
    }
}
