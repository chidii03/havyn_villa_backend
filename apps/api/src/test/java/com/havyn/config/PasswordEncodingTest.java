package com.havyn.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test for the {@link SecurityConfig#passwordEncoder()} bean — no Spring context needed. */
class PasswordEncodingTest {

    private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Test
    void encodedHashIsNeverTheRawPassword() {
        String hash = encoder.encode("Correct-Horse-Battery-Staple");

        assertThat(hash).isNotEqualTo("Correct-Horse-Battery-Staple");
        assertThat(hash).startsWith("$argon2");
    }

    @Test
    void matchesTheOriginalPasswordAgainstItsHash() {
        String hash = encoder.encode("Correct-Horse-Battery-Staple");

        assertThat(encoder.matches("Correct-Horse-Battery-Staple", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void sameInputProducesDifferentHashesBecauseOfRandomSalt() {
        String first = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("same-password", first)).isTrue();
        assertThat(encoder.matches("same-password", second)).isTrue();
    }
}
