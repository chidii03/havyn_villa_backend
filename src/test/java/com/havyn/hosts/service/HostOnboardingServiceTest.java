package com.havyn.hosts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.auth.domain.AuthResult;
import com.havyn.auth.domain.AuthService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostOnboardingServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private final HostOnboardingService service = new HostOnboardingService(userRepository, roleRepository, authService);

    private final UUID userId = UUID.randomUUID();

    private User verifiedUser() {
        User user = new User("guest@example.com", "hashed");
        user.markEmailVerified(Instant.now());
        return user;
    }

    @Test
    void becomeHost_rejectsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.becomeHost(userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void becomeHost_rejectsAnUnverifiedEmail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User("guest@example.com", "hashed")));

        assertThatThrownBy(() -> service.becomeHost(userId))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void becomeHost_grantsTheHostRoleAndReissuesTokens() {
        User user = verifiedUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Role hostRole = new Role("HOST", "Host");
        when(roleRepository.findByCode("HOST")).thenReturn(Optional.of(hostRole));
        AuthResult expected = new AuthResult("access-token", "refresh-token", 900, user);
        when(authService.reissueTokens(user)).thenReturn(expected);

        AuthResult result = service.becomeHost(userId);

        assertThat(user.getRoles()).extracting(Role::getCode).containsExactly("HOST");
        assertThat(result).isSameAs(expected);
        verify(authService).reissueTokens(user);
    }

    @Test
    void becomeHost_isIdempotentForAnAlreadyHostUser() {
        User user = verifiedUser();
        user.addRole(new Role("HOST", "Host"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authService.reissueTokens(any())).thenReturn(new AuthResult("access-token", "refresh-token", 900, user));

        service.becomeHost(userId);

        assertThat(user.getRoles()).extracting(Role::getCode).containsExactly("HOST");
        verify(roleRepository, org.mockito.Mockito.never()).findByCode(any());
    }
}
