package com.havyn.hosts.service;

import com.havyn.booking.service.BookingService;
import com.havyn.hosts.web.CurrencyAmount;
import com.havyn.hosts.web.HostDashboardSummary;
import com.havyn.payments.domain.Payout;
import com.havyn.payments.domain.PayoutStatus;
import com.havyn.payments.service.PaymentService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.repo.PropertyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard overview — see project-docs/prompts/17-host-dashboard.md's "performance"
 * deliverable. Deliberately scoped to figures derivable from data this project already
 * persists (listings, bookings, payouts) rather than a new page-view/analytics
 * pipeline — no such tracking infrastructure exists anywhere in this codebase, and
 * building one is out of proportion to this prompt's actual acceptance criteria ("view
 * accurate earnings/payouts"), which never names occupancy/traffic metrics. Every
 * figure here is computed in this backend service, never in the frontend — see this
 * prompt's own "No business math on the frontend" constraint.
 */
@Service
public class HostDashboardService {

    private final PropertyRepository propertyRepository;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    public HostDashboardService(PropertyRepository propertyRepository, BookingService bookingService, PaymentService paymentService) {
        this.propertyRepository = propertyRepository;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public HostDashboardSummary summary(UUID hostId) {
        List<Property> properties = propertyRepository.findAllByHostId(hostId, Pageable.unpaged()).getContent();
        int total = properties.size();
        int active = (int) properties.stream().filter(property -> property.getStatus() == PropertyStatus.ACTIVE).count();

        long upcomingReservations = bookingService.countUpcomingForHost(hostId);

        List<Payout> payouts = paymentService.listAllPayoutsForHost(hostId);
        List<CurrencyAmount> totalEarnings = payouts.stream()
                .collect(Collectors.groupingBy(Payout::getCurrency, TreeMap::new, Collectors.reducing(BigDecimal.ZERO, Payout::getAmount, BigDecimal::add)))
                .entrySet()
                .stream()
                .map(entry -> new CurrencyAmount(entry.getKey(), entry.getValue()))
                .toList();
        long pendingPayouts = payouts.stream().filter(payout -> payout.getStatus() == PayoutStatus.PENDING).count();

        BigDecimal averageRating = averageRating(properties);

        return new HostDashboardSummary(active, total, upcomingReservations, totalEarnings, pendingPayouts, averageRating);
    }

    private BigDecimal averageRating(List<Property> properties) {
        List<Property> rated = properties.stream().filter(property -> property.getRatingCount() > 0).toList();
        if (rated.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = rated.stream().map(Property::getRatingAvg).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rated.size()), 2, RoundingMode.HALF_UP);
    }
}
