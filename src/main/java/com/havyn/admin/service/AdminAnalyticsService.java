package com.havyn.admin.service;

import com.havyn.admin.web.AdminAnalyticsSummary;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.repo.UserRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-wide analytics — see project-docs/prompts/18-admin-platform.md's "view
 * analytics" acceptance criterion. Deliberately computed from data this project
 * already persists (users, properties, bookings, verification requests, disputes),
 * not a new tracking/analytics pipeline — no page-view/traffic instrumentation
 * exists anywhere in this codebase, and building one for this prompt alone would be
 * scope creep, same reasoning session 18's "performance" scoping note already
 * established for the host dashboard.
 */
@Service
public class AdminAnalyticsService {

    private static final Set<BookingStatus> REVENUE_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final VerificationService verificationService;
    private final DisputeService disputeService;

    public AdminAnalyticsService(
            UserRepository userRepository,
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            VerificationService verificationService,
            DisputeService disputeService) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.verificationService = verificationService;
        this.disputeService = disputeService;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsSummary summary() {
        long totalUsers = userRepository.count();
        long totalHosts = userRepository.countByRoles_Code("HOST");
        long totalProperties = propertyRepository.count();
        long activeProperties = propertyRepository.countByStatus(PropertyStatus.ACTIVE);
        long totalBookings = bookingRepository.count();
        long revenueBookings = bookingRepository.countByStatusIn(REVENUE_STATUSES);

        return new AdminAnalyticsSummary(
                totalUsers,
                totalHosts,
                totalProperties,
                activeProperties,
                totalBookings,
                revenueBookings,
                bookingRepository.sumGrandTotalByStatusIn(REVENUE_STATUSES),
                bookingRepository.sumCommissionAmountByStatusIn(REVENUE_STATUSES),
                verificationService.countPending(),
                disputeService.countOpen());
    }
}
