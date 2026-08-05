package com.havyn.admin.service;

import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.User;
import com.havyn.users.domain.UserStatus;
import com.havyn.users.repo.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user management — see project-docs/prompts/18-admin-platform.md. Only
 * {@code HOST}/{@code ADMIN} are grantable/revocable this way (not {@code CUSTOMER} —
 * the baseline role every registration already assigns). Every mutation is
 * audit-logged in the same transaction as the change itself.
 */
@Service
public class AdminUserService {

    private static final Set<String> GRANTABLE_ROLE_CODES = Set.of("HOST", "ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    public AdminUserService(UserRepository userRepository, RoleRepository roleRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<User> list(String emailSearch, Pageable pageable) {
        if (emailSearch != null && !emailSearch.isBlank()) {
            return userRepository.findAllByEmailContainingIgnoreCase(emailSearch, pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User grantRole(UUID adminId, UUID userId, String roleCode) {
        validateGrantableRole(roleCode);
        User user = findUser(userId);
        Role role = findRole(roleCode);
        Set<String> before = roleCodes(user);
        user.addRole(role);
        auditLogService.record(adminId, "USER_ROLE_GRANTED", "User", userId, Map.of("roles", before), Map.of("roles", roleCodes(user)));
        return user;
    }

    @Transactional
    public User revokeRole(UUID adminId, UUID userId, String roleCode) {
        validateGrantableRole(roleCode);
        if (adminId.equals(userId) && "ADMIN".equals(roleCode)) {
            throw new BadRequestException("CANNOT_REVOKE_OWN_ADMIN_ROLE", "You cannot revoke your own admin role");
        }
        User user = findUser(userId);
        Role role = findRole(roleCode);
        Set<String> before = roleCodes(user);
        user.removeRole(role);
        auditLogService.record(adminId, "USER_ROLE_REVOKED", "User", userId, Map.of("roles", before), Map.of("roles", roleCodes(user)));
        return user;
    }

    @Transactional
    public User suspend(UUID adminId, UUID userId) {
        User user = findUser(userId);
        UserStatus before = user.getStatus();
        user.setStatus(UserStatus.SUSPENDED);
        auditLogService.record(adminId, "USER_SUSPENDED", "User", userId, Map.of("status", before), Map.of("status", user.getStatus()));
        return user;
    }

    @Transactional
    public User reactivate(UUID adminId, UUID userId) {
        User user = findUser(userId);
        UserStatus before = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        auditLogService.record(adminId, "USER_REACTIVATED", "User", userId, Map.of("status", before), Map.of("status", user.getStatus()));
        return user;
    }

    private void validateGrantableRole(String roleCode) {
        if (!GRANTABLE_ROLE_CODES.contains(roleCode)) {
            throw new BadRequestException("INVALID_ROLE", "Role must be one of " + GRANTABLE_ROLE_CODES);
        }
    }

    private Role findRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException(roleCode + " role must be seeded by V1__init.sql"));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toCollection(TreeSet::new));
    }
}
