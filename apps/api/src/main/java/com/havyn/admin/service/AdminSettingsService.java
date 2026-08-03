package com.havyn.admin.service;

import com.havyn.audit.service.AuditLogService;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.NotFoundException;
import com.havyn.pricing.domain.PlatformSetting;
import com.havyn.pricing.repo.PlatformSettingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission/platform settings — see project-docs/prompts/18-admin-platform.md's
 * "Commissions/settings are configurable data, never hardcoded" constraint.
 * {@code PlatformSetting} already existed (session 6, seeded by V5__booking.sql) —
 * its own Javadoc explicitly deferred a read/write API to this prompt; nothing new
 * needed there beyond the one setter this session added. {@code PricingService}
 * reads {@code commission_pct} fresh from the DB on every quote (no caching), so an
 * update here takes effect immediately.
 */
@Service
public class AdminSettingsService {

    private static final String COMMISSION_SETTING_KEY = "commission_pct";
    private static final String BOOKINGS_ENABLED_SETTING_KEY = "bookings_enabled";

    private final PlatformSettingRepository platformSettingRepository;
    private final AuditLogService auditLogService;

    public AdminSettingsService(PlatformSettingRepository platformSettingRepository, AuditLogService auditLogService) {
        this.platformSettingRepository = platformSettingRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PlatformSetting> list() {
        return platformSettingRepository.findAll();
    }

    @Transactional
    public PlatformSetting update(UUID adminId, String key, String value) {
        PlatformSetting setting = platformSettingRepository.findById(key).orElseThrow(() -> NotFoundException.of("PlatformSetting", key));
        validateValue(key, value);
        String before = setting.getValue();
        setting.setValue(value);
        auditLogService.record(
                adminId, "PLATFORM_SETTING_UPDATED", "PlatformSetting", null,
                Map.of("key", key, "value", before), Map.of("key", key, "value", value));
        return setting;
    }

    private void validateValue(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("INVALID_SETTING_VALUE", "Value must not be blank");
        }
        if (COMMISSION_SETTING_KEY.equals(key)) {
            BigDecimal parsed;
            try {
                parsed = new BigDecimal(value);
            } catch (NumberFormatException e) {
                throw new BadRequestException("INVALID_SETTING_VALUE", "commission_pct must be a number");
            }
            if (parsed.signum() < 0 || parsed.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("INVALID_SETTING_VALUE", "commission_pct must be between 0 and 100");
            }
        }
        if (BOOKINGS_ENABLED_SETTING_KEY.equals(key) && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BadRequestException("INVALID_SETTING_VALUE", "bookings_enabled must be \"true\" or \"false\"");
        }
    }
}
