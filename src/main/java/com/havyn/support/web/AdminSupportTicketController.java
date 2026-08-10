package com.havyn.support.web;

import com.havyn.common.web.PageResponse;
import com.havyn.support.domain.SupportTicketStatus;
import com.havyn.support.repo.SupportTicketRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportTicketController {

    private final SupportTicketRepository repository;

    public AdminSupportTicketController(SupportTicketRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<SupportTicketSummary> list(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        if (status == null && normalizedSearch == null) {
            return PageResponse.of(repository.findAllByOrderByCreatedAtDesc(pageable).map(SupportTicketSummary::from));
        }
        if (status == null) {
            return PageResponse.of(repository.search(normalizedSearch, pageable).map(SupportTicketSummary::from));
        }
        if (normalizedSearch == null) {
            return PageResponse.of(repository.findByStatusOrderByCreatedAtDesc(status, pageable).map(SupportTicketSummary::from));
        }
        return PageResponse.of(repository.searchByStatus(status, normalizedSearch, pageable).map(SupportTicketSummary::from));
    }
}
