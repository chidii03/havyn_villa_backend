package com.havyn.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.reference.Role;
import com.havyn.common.reference.RoleRepository;
import com.havyn.users.domain.User;
import com.havyn.users.domain.UserStatus;
import com.havyn.users.repo.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AdminUserService service = new AdminUserService(userRepository, roleRepository, auditLogService);

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("guest@example.com", "hashed");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void grantRole_rejectsAnUngrantableRoleCode() {
        assertThatThrownBy(() -> service.grantRole(adminId, userId, "CUSTOMER"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_ROLE");
    }

    @Test
    void grantRole_addsTheRoleAndRecordsAnAuditEntry() {
        when(roleRepository.findByCode("HOST")).thenReturn(Optional.of(new Role("HOST", "Host")));

        User result = service.grantRole(adminId, userId, "HOST");

        assertThat(result.getRoles()).extracting(Role::getCode).containsExactly("HOST");
        verify(auditLogService).record(eq(adminId), eq("USER_ROLE_GRANTED"), eq("User"), eq(userId), any(), any());
    }

    @Test
    void revokeRole_rejectsAnAdminRevokingTheirOwnAdminRole() {
        assertThatThrownBy(() -> service.revokeRole(adminId, adminId, "ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("CANNOT_REVOKE_OWN_ADMIN_ROLE");
    }

    @Test
    void revokeRole_removesTheRole() {
        Role hostRole = new Role("HOST", "Host");
        user.addRole(hostRole);
        when(roleRepository.findByCode("HOST")).thenReturn(Optional.of(hostRole));

        User result = service.revokeRole(adminId, userId, "HOST");

        assertThat(result.getRoles()).isEmpty();
    }

    @Test
    void suspend_rejectsAMissingUser() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(adminId, missingId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void suspendThenReactivate_flipsUserStatus() {
        service.suspend(adminId, userId);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);

        service.reactivate(adminId, userId);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
