package com.havyn.notifications.web;

import com.havyn.common.web.PageResponse;
import com.havyn.notifications.domain.BookingEmailStatus;
import com.havyn.notifications.repo.BookingEmailLogRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/emails")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingEmailController {

    private final BookingEmailLogRepository repository;

    public AdminBookingEmailController(BookingEmailLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public BookingEmailLogPage list(
            @RequestParam(required = false) BookingEmailStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return new BookingEmailLogPage(
                repository.count(),
                repository.countByStatus(BookingEmailStatus.SUCCESSFUL),
                repository.countByStatus(BookingEmailStatus.FAILED),
                PageResponse.of(logs(status, normalizedSearch, pageable).map(BookingEmailLogSummary::from)));
    }

    private org.springframework.data.domain.Page<com.havyn.notifications.domain.BookingEmailLog> logs(
            BookingEmailStatus status,
            String search,
            Pageable pageable) {
        if (status == null && search == null) {
            return repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        if (status == null) {
            return repository.search(search, pageable);
        }
        if (search == null) {
            return repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return repository.searchByStatus(status, search, pageable);
    }
}
