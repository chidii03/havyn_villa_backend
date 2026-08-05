package com.havyn.hosts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.booking.service.BookingService;
import com.havyn.hosts.web.CurrencyAmount;
import com.havyn.hosts.web.HostDashboardSummary;
import com.havyn.payments.domain.Payout;
import com.havyn.payments.service.PaymentService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class HostDashboardServiceTest {

    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final PaymentService paymentService = mock(PaymentService.class);

    private final HostDashboardService service = new HostDashboardService(propertyRepository, bookingService, paymentService);

    private final UUID hostId = UUID.randomUUID();

    private Property activeProperty(BigDecimal ratingAvg, int ratingCount) {
        PropertyType villa = mock(PropertyType.class);
        Property property = new Property(
                hostId, villa, "Sunset Villa " + UUID.randomUUID(), "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        property.transitionTo(PropertyStatus.PENDING);
        property.transitionTo(PropertyStatus.ACTIVE);
        property.applyAggregateRating(ratingAvg, ratingCount);
        return property;
    }

    // Every Payout row is PENDING today — no payout-execution rail exists yet to ever
    // move one to PAID/FAILED (see PayoutStatus's own Javadoc) — so that's the only
    // status this fixture can honestly construct.
    private Payout pendingPayout(String period, BigDecimal amount, String currency) {
        Payout payout = new Payout(hostId, period, currency);
        payout.accrue(amount);
        return payout;
    }

    @Test
    void summary_countsActiveAndTotalListingsAndAveragesOnlyRatedOnes() {
        Property rated = activeProperty(BigDecimal.valueOf(4.50), 3);
        Property unrated = activeProperty(BigDecimal.ZERO, 0);
        when(propertyRepository.findAllByHostId(hostId, Pageable.unpaged()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(rated, unrated)));
        when(bookingService.countUpcomingForHost(hostId)).thenReturn(2L);
        when(paymentService.listAllPayoutsForHost(hostId)).thenReturn(List.of());

        HostDashboardSummary summary = service.summary(hostId);

        assertThat(summary.totalListingsCount()).isEqualTo(2);
        assertThat(summary.activeListingsCount()).isEqualTo(2);
        assertThat(summary.upcomingReservationsCount()).isEqualTo(2L);
        // Only the rated listing counts toward the average — an unrated new listing shouldn't drag it down.
        assertThat(summary.averageRating()).isEqualByComparingTo("4.50");
    }

    @Test
    void summary_sumsEarningsByCurrencyAndCountsPendingPayouts() {
        when(propertyRepository.findAllByHostId(hostId, Pageable.unpaged())).thenReturn(Page.empty());
        when(bookingService.countUpcomingForHost(hostId)).thenReturn(0L);
        when(paymentService.listAllPayoutsForHost(hostId)).thenReturn(List.of(
                pendingPayout("2026-06", BigDecimal.valueOf(50000), "NGN"),
                pendingPayout("2026-07", BigDecimal.valueOf(30000), "NGN"),
                pendingPayout("2026-07", BigDecimal.valueOf(100), "USD")));

        HostDashboardSummary summary = service.summary(hostId);

        assertThat(summary.totalEarnings()).containsExactlyInAnyOrder(
                new CurrencyAmount("NGN", BigDecimal.valueOf(80000)), new CurrencyAmount("USD", BigDecimal.valueOf(100)));
        assertThat(summary.pendingPayoutsCount()).isEqualTo(3L);
    }

    @Test
    void summary_returnsZeroesForAHostWithNoDataYet() {
        when(propertyRepository.findAllByHostId(hostId, Pageable.unpaged())).thenReturn(Page.empty());
        when(bookingService.countUpcomingForHost(hostId)).thenReturn(0L);
        when(paymentService.listAllPayoutsForHost(hostId)).thenReturn(List.of());

        HostDashboardSummary summary = service.summary(hostId);

        assertThat(summary.totalListingsCount()).isZero();
        assertThat(summary.activeListingsCount()).isZero();
        assertThat(summary.upcomingReservationsCount()).isZero();
        assertThat(summary.totalEarnings()).isEmpty();
        assertThat(summary.pendingPayoutsCount()).isZero();
        assertThat(summary.averageRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
