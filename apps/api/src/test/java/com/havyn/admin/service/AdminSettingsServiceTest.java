package com.havyn.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.pricing.domain.PlatformSetting;
import com.havyn.pricing.repo.PlatformSettingRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminSettingsServiceTest {

    private final PlatformSettingRepository platformSettingRepository = mock(PlatformSettingRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AdminSettingsService service = new AdminSettingsService(platformSettingRepository, auditLogService);

    private final UUID adminId = UUID.randomUUID();

    @Test
    void update_rejectsAMissingSetting() {
        when(platformSettingRepository.findById("unknown_key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(adminId, "unknown_key", "5")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_rejectsABlankValue() {
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(new PlatformSetting("commission_pct", "12.00")));

        assertThatThrownBy(() -> service.update(adminId, "commission_pct", "   "))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_SETTING_VALUE");
    }

    @Test
    void update_rejectsANonNumericCommissionPct() {
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(new PlatformSetting("commission_pct", "12.00")));

        assertThatThrownBy(() -> service.update(adminId, "commission_pct", "not-a-number"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_SETTING_VALUE");
    }

    @Test
    void update_rejectsACommissionPctOutOfRange() {
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(new PlatformSetting("commission_pct", "12.00")));

        assertThatThrownBy(() -> service.update(adminId, "commission_pct", "150"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_SETTING_VALUE");
    }

    @Test
    void update_appliesAValidNewCommissionPct() {
        PlatformSetting setting = new PlatformSetting("commission_pct", "12.00");
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(setting));

        PlatformSetting result = service.update(adminId, "commission_pct", "15.00");

        assertThat(result.getValue()).isEqualTo("15.00");
    }

    @Test
    void update_rejectsANonBooleanValueForBookingsEnabled() {
        when(platformSettingRepository.findById("bookings_enabled")).thenReturn(Optional.of(new PlatformSetting("bookings_enabled", "true")));

        assertThatThrownBy(() -> service.update(adminId, "bookings_enabled", "disabled"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_SETTING_VALUE");
    }

    @Test
    void update_appliesTheBookingsEnabledKillSwitch_launchChecklistFix() {
        PlatformSetting setting = new PlatformSetting("bookings_enabled", "true");
        when(platformSettingRepository.findById("bookings_enabled")).thenReturn(Optional.of(setting));

        PlatformSetting result = service.update(adminId, "bookings_enabled", "false");

        assertThat(result.getValue()).isEqualTo("false");
    }
}
