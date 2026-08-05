package com.havyn.search.web;

import com.havyn.common.web.PageResponse;
import com.havyn.search.service.SearchCriteria;
import com.havyn.search.service.SearchService;
import com.havyn.search.service.SortOption;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /search} — see project-docs/prompts/11-search-discovery.md. Public, no auth required. */
@RestController
@RequestMapping("/api/v1/search")
@Validated
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public PageResponse<SearchResultItem> search(
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) @Positive Integer guests,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @Positive Integer bedrooms,
            @RequestParam(required = false) Set<String> amenities,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal rating,
            @RequestParam(required = false) String sort,
            @PageableDefault(size = 20) Pageable pageable) {
        SearchCriteria criteria = new SearchCriteria(
                destination, checkIn, checkOut, guests, minPrice, maxPrice, type, bedrooms, amenities, rating,
                SortOption.fromParam(sort));
        return searchService.search(criteria, pageable);
    }
}
