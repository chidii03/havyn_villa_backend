package com.havyn.hosts.service;

import com.havyn.auth.domain.AuthResult;
import com.havyn.auth.domain.AuthService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-serve "become a host" — see project-docs/prompts/17-host-dashboard.md. Grants
 * the {@code HOST} role (idempotent — already-hosts just get fresh tokens back, no
 * error) and re-mints tokens via {@code AuthService#reissueTokens} so the caller's
 * next request can immediately use {@code @PreAuthorize("hasRole('HOST')")} endpoints
 * without a full logout/login. Requires a verified email — a real marketplace's supply
 * side needs at least that much accountability before anyone can list a property.
 *
 * Mutates {@code users.domain.User} directly via {@code User#addRole}, a public method
 * that entity already exposes for exactly this — the same category of minimal,
 * necessary cross-module write {@code BookingService} already makes on
 * {@code properties.domain.Availability} (see database/01-data-model.md's session 4
 * notes on {@code Availability.setBookingId}).
 */
@Service
public class HostOnboardingService {

    private static final String HOST_ROLE_CODE = "HOST";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;

    public HostOnboardingService(UserRepository userRepository, RoleRepository roleRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
    }

    @Transactional
    public AuthResult becomeHost(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (!user.isEmailVerified()) {
            throw new BadRequestException("EMAIL_NOT_VERIFIED", "Verify your email before you can host");
        }

        boolean alreadyHost = user.getRoles().stream().anyMatch(role -> role.getCode().equals(HOST_ROLE_CODE));
        if (!alreadyHost) {
            Role hostRole = roleRepository.findByCode(HOST_ROLE_CODE)
                    .orElseThrow(() -> new IllegalStateException(HOST_ROLE_CODE + " role must be seeded by V1__init.sql"));
            user.addRole(hostRole);
        }

        return authService.reissueTokens(user);
    }
}
