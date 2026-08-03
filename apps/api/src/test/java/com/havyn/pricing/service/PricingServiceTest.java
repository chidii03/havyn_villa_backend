package com.havyn.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.pricing.domain.PlatformSetting;
import com.havyn.pricing.repo.PlatformSettingRepository;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.AvailabilityRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final AvailabilityRepository availabilityRepository = mock(AvailabilityRepository.class);
    private final PlatformSettingRepository platformSettingRepository = mock(PlatformSettingRepository.class);
    private final PricingService service = new PricingService(availabilityRepository, platformSettingRepository);

    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        property.setCleaningFee(BigDecimal.valueOf(2000));
        property.setServiceFeePct(BigDecimal.valueOf(10));
        when(availabilityRepository.findAllByProperty_IdAndDateBetweenOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void computesBaseTotalAcrossNightsWithNoOverrides() {
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.empty());

        PricingBreakdown breakdown = service.quote(property, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4)); // 3 nights

        assertThat(breakdown.nights()).isEqualTo(3);
        assertThat(breakdown.baseTotal()).isEqualByComparingTo("30000");
        assertThat(breakdown.cleaningFee()).isEqualByComparingTo("2000");
        assertThat(breakdown.serviceFee()).isEqualByComparingTo("3000"); // 10% of 30000
        assertThat(breakdown.discountTotal()).isEqualByComparingTo("0");
        assertThat(breakdown.taxTotal()).isEqualByComparingTo("0");
        assertThat(breakdown.grandTotal()).isEqualByComparingTo("35000"); // 30000 + 2000 + 3000
        assertThat(breakdown.currency()).isEqualTo("NGN");
    }

    @Test
    void appliesAPerDateAvailabilityOverride() {
        LocalDate checkIn = LocalDate.of(2026, 8, 1);
        LocalDate checkOut = LocalDate.of(2026, 8, 3); // 2 nights: Aug 1 (override), Aug 2 (base)
        Availability override = new Availability(property, checkIn, false, BigDecimal.valueOf(25000));
        // Service queries [checkIn, checkOut - 1 day] — must match that exact window, not [checkIn, checkIn].
        when(availabilityRepository.findAllByProperty_IdAndDateBetweenOrderByDateAsc(
                        property.getId(), checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(override));
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.empty());

        PricingBreakdown breakdown = service.quote(property, checkIn, checkOut);

        // Aug 1 at override (25000) + Aug 2 at base (10000)
        assertThat(breakdown.baseTotal()).isEqualByComparingTo("35000");
    }

    @Test
    void usesTheConfiguredCommissionPctFromPlatformSettings() {
        PlatformSetting setting = mock(PlatformSetting.class);
        when(setting.getValue()).thenReturn("15.00");
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(setting));

        PricingBreakdown breakdown = service.quote(property, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)); // 1 night

        // commission = (baseTotal + cleaningFee) * 15% = (10000 + 2000) * 0.15 = 1800
        assertThat(breakdown.commissionAmount()).isEqualByComparingTo("1800");
    }

    @Test
    void fallsBackToTheDefaultCommissionPctWhenNoSettingIsSeeded() {
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.empty());

        PricingBreakdown breakdown = service.quote(property, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)); // 1 night

        // default 12% of (10000 + 2000) = 1440
        assertThat(breakdown.commissionAmount()).isEqualByComparingTo("1440");
    }

    @Test
    void commissionIsNotAddedToTheGuestFacingGrandTotal() {
        PlatformSetting setting = mock(PlatformSetting.class);
        when(setting.getValue()).thenReturn("50.00"); // deliberately large to make any leakage obvious
        when(platformSettingRepository.findById("commission_pct")).thenReturn(Optional.of(setting));

        PricingBreakdown breakdown = service.quote(property, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)); // 1 night

        // grandTotal = base(10000) + cleaning(2000) + service(1000) = 13000, regardless of commission
        assertThat(breakdown.grandTotal()).isEqualByComparingTo("13000");
    }
}
