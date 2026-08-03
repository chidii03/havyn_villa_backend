package com.havyn.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.havyn.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Rotation + reuse detection against real Redis (Testcontainers) — requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenServiceIT {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void rotatingAValidTokenIssuesADifferentTokenInTheSameFamily() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.Issued issued = refreshTokenService.issue(userId);

        RefreshTokenService.Issued rotated = refreshTokenService.rotate(issued.token());

        assertThat(rotated.token()).isNotEqualTo(issued.token());
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.familyId()).isEqualTo(issued.familyId());
    }

    @Test
    void rotatingTheSameTokenTwiceFailsTheSecondTime() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.Issued issued = refreshTokenService.issue(userId);

        refreshTokenService.rotate(issued.token()); // first rotation succeeds and consumes it

        assertThatThrownBy(() -> refreshTokenService.rotate(issued.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reusingAnAlreadyRotatedTokenRevokesTheWholeFamily() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.Issued issued = refreshTokenService.issue(userId);
        RefreshTokenService.Issued rotated = refreshTokenService.rotate(issued.token());

        // Reuse of the old (already-rotated) token is rejected...
        assertThatThrownBy(() -> refreshTokenService.rotate(issued.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // ...and it took the legitimate, newly-rotated token down with it.
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllForUserInvalidatesEverySessionFamily() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.Issued sessionA = refreshTokenService.issue(userId);
        RefreshTokenService.Issued sessionB = refreshTokenService.issue(userId);

        refreshTokenService.revokeAllForUser(userId);

        assertThatThrownBy(() -> refreshTokenService.rotate(sessionA.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(sessionB.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.Issued issued = refreshTokenService.issue(userId);
        refreshTokenService.revokeFamily(issued.familyId());

        assertThatThrownBy(() -> refreshTokenService.rotate(issued.token()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
