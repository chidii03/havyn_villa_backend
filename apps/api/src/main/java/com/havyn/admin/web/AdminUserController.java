package com.havyn.admin.web;

import com.havyn.admin.service.AdminUserService;
import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.common.web.PageResponse;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin user management — see project-docs/prompts/18-admin-platform.md. */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final ProfileRepository profileRepository;

    public AdminUserController(AdminUserService adminUserService, ProfileRepository profileRepository) {
        this.adminUserService = adminUserService;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public PageResponse<AdminUserSummary> list(
            @RequestParam(required = false) String email, @PageableDefault(size = 20) Pageable pageable) {
        Page<User> users = adminUserService.list(email, pageable);
        List<UUID> userIds = users.getContent().stream().map(User::getId).toList();
        Map<UUID, String> fullNames = new HashMap<>();
        profileRepository.findAllByUser_IdIn(userIds).forEach(profile -> fullNames.put(profile.getUser().getId(), profile.getFullName()));
        return PageResponse.of(users.map(user -> AdminUserSummary.from(user, fullNames.get(user.getId()))));
    }

    @PostMapping("/{id}/roles/{roleCode}")
    public AdminUserSummary grantRole(Authentication authentication, @PathVariable UUID id, @PathVariable String roleCode) {
        User user = adminUserService.grantRole(principal(authentication), id, roleCode);
        return toSummary(user);
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    public AdminUserSummary revokeRole(Authentication authentication, @PathVariable UUID id, @PathVariable String roleCode) {
        User user = adminUserService.revokeRole(principal(authentication), id, roleCode);
        return toSummary(user);
    }

    @PostMapping("/{id}/suspend")
    public AdminUserSummary suspend(Authentication authentication, @PathVariable UUID id) {
        return toSummary(adminUserService.suspend(principal(authentication), id));
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserSummary reactivate(Authentication authentication, @PathVariable UUID id) {
        return toSummary(adminUserService.reactivate(principal(authentication), id));
    }

    private AdminUserSummary toSummary(User user) {
        String fullName = profileRepository.findByUser_Id(user.getId()).map(Profile::getFullName).orElse(null);
        return AdminUserSummary.from(user, fullName);
    }

    private UUID principal(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
