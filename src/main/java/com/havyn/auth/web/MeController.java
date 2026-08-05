package com.havyn.auth.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import com.havyn.users.repo.UserRepository;
import jakarta.validation.Valid;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated caller's own account — see project-docs/architecture/03-api-design.md. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public MeController(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public UserSummary me(Authentication authentication) {
        return loadSummary(principal(authentication));
    }

    @PatchMapping
    public UserSummary updateMe(Authentication authentication, @Valid @RequestBody UpdateMeRequest request) {
        AuthenticatedUser principal = principal(authentication);
        Profile profile = profileRepository.findByUser_Id(principal.userId())
                .orElseThrow(() -> NotFoundException.of("Profile", principal.userId()));
        if (request.fullName() != null) {
            profile.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone());
        }
        profileRepository.save(profile);
        return loadSummary(principal);
    }

    private UserSummary loadSummary(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> NotFoundException.of("User", principal.userId()));
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

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
