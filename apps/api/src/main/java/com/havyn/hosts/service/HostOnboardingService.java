package com.havyn.hosts.service;

import com.havyn.admin.domain.VerificationStatus;
import com.havyn.admin.repo.VerificationRequestRepository;
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

@Service
public class HostOnboardingService {

    private static final String HOST_ROLE_CODE = "HOST";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final AuthService authService;

    public HostOnboardingService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            VerificationRequestRepository verificationRequestRepository,
            AuthService authService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationRequestRepository = verificationRequestRepository;
        this.authService = authService;
    }

    @Transactional
    public AuthResult becomeHost(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        if (!user.isEmailVerified()) {
            authService.sendEmailVerification(user);
            throw new BadRequestException("EMAIL_NOT_VERIFIED", "Verify your email before you can host");
        }
        if (!verificationRequestRepository.existsByUserIdAndStatus(userId, VerificationStatus.APPROVED)) {
            throw new BadRequestException("KYC_NOT_APPROVED", "Complete host verification before you can host");
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
