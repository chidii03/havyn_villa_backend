package com.havyn.pricing.service;

import com.havyn.pricing.repo.PlatformSettingRepository;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.AvailabilityRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Authoritative, deterministic pricing — see project-docs/backend/02-domain-modules.md#pricing-authoritative.
 * {@code nights x base (+ per-date overrides) + cleaning + service fee - discounts +
 * taxes}. Discounts and taxes are real fields in the response, always {@code 0} today
 * — there's no promo-code system or tax-jurisdiction config anywhere in this project
 * yet, so computing anything else would be fabricating a number, not "0 because
 * nothing applies."
 */
@Service
public class PricingService {

    private static final String COMMISSION_SETTING_KEY = "commission_pct";
    // "default illustrative 12-15%" — project-docs/product/03-business-model.md#17.
    // Only used if the seeded platform_setting row is somehow missing.
    private static final BigDecimal DEFAULT_COMMISSION_PCT = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AvailabilityRepository availabilityRepository;
    private final PlatformSettingRepository platformSettingRepository;

    public PricingService(AvailabilityRepository availabilityRepository, PlatformSettingRepository platformSettingRepository) {
        this.availabilityRepository = availabilityRepository;
        this.platformSettingRepository = platformSettingRepository;
    }

    public PricingBreakdown quote(Property property, LocalDate checkIn, LocalDate checkOut) {
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        Map<LocalDate, BigDecimal> overridesByDate = availabilityRepository
                .findAllByProperty_IdAndDateBetweenOrderByDateAsc(property.getId(), checkIn, checkOut.minusDays(1))
                .stream()
                .filter(a -> a.getPriceOverride() != null)
                .collect(Collectors.toMap(Availability::getDate, Availability::getPriceOverride, (a, b) -> b));

        BigDecimal baseTotal = BigDecimal.ZERO;
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            baseTotal = baseTotal.add(overridesByDate.getOrDefault(date, property.getBasePrice()));
        }

        BigDecimal cleaningFee = property.getCleaningFee();
        BigDecimal serviceFee = percentOf(baseTotal, property.getServiceFeePct());
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = baseTotal.add(cleaningFee).add(serviceFee).subtract(discountTotal).add(taxTotal)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal commissionAmount = percentOf(baseTotal.add(cleaningFee), commissionPct());

        return new PricingBreakdown(
                nights, baseTotal, cleaningFee, serviceFee, discountTotal, taxTotal, grandTotal, commissionAmount,
                property.getCurrency());
    }

    private BigDecimal commissionPct() {
        return platformSettingRepository.findById(COMMISSION_SETTING_KEY)
                .map(setting -> new BigDecimal(setting.getValue()))
                .orElse(DEFAULT_COMMISSION_PCT);
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal pct) {
        return amount.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
