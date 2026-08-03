package com.havyn.search.service;

import com.havyn.common.error.BadRequestException;
import com.havyn.common.web.PageResponse;
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.domain.Property;
import com.havyn.search.cache.CachedSearchPage;
import com.havyn.search.cache.SearchCacheService;
import com.havyn.search.repo.PropertySearchRepository;
import com.havyn.search.web.SearchResultItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Orchestrates {@code GET /search}: validate -> cache lookup -> query -> cache fill. */
@Service
public class SearchService {

    private final PropertySearchRepository propertySearchRepository;
    private final SearchCacheService searchCacheService;
    // Cross-module read (media/ owns PropertyMedia) — same established pattern as
    // NotificationService reading properties/ and users/ directly.
    private final PropertyMediaRepository propertyMediaRepository;

    public SearchService(
            PropertySearchRepository propertySearchRepository,
            SearchCacheService searchCacheService,
            PropertyMediaRepository propertyMediaRepository) {
        this.propertySearchRepository = propertySearchRepository;
        this.searchCacheService = searchCacheService;
        this.propertyMediaRepository = propertyMediaRepository;
    }

    public PageResponse<SearchResultItem> search(SearchCriteria criteria, Pageable pageable) {
        validate(criteria);

        return searchCacheService.get(criteria, pageable)
                .map(CachedSearchPage::toPageResponse)
                .orElseGet(() -> {
                    PageResponse<SearchResultItem> fresh = execute(criteria, pageable);
                    searchCacheService.put(criteria, pageable, CachedSearchPage.from(fresh));
                    return fresh;
                });
    }

    private PageResponse<SearchResultItem> execute(SearchCriteria criteria, Pageable pageable) {
        Page<Property> results = propertySearchRepository.search(criteria, pageable);
        Map<UUID, List<String>> photoUrlsByPropertyId = photoUrlsByPropertyId(results.getContent());
        // Always approximate — GET /search is always public; see
        // SearchResultItem#withApproximateLocation's own doc.
        return PageResponse.of(results.map(property -> SearchResultItem
                .from(property, photoUrlsByPropertyId.getOrDefault(property.getId(), List.of()))
                .withApproximateLocation()));
    }

    /** One query for the whole page instead of N — see PropertyMediaRepository's own doc on the ordering guarantee. */
    private Map<UUID, List<String>> photoUrlsByPropertyId(List<Property> properties) {
        List<UUID> propertyIds = properties.stream().map(Property::getId).toList();
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return propertyMediaRepository.findAllByPropertyIdInOrderByPositionAsc(propertyIds).stream()
                .collect(Collectors.groupingBy(
                        PropertyMedia::getPropertyId,
                        Collectors.mapping(PropertyMedia::getSecureUrl, Collectors.toList())));
    }

    private void validate(SearchCriteria criteria) {
        boolean hasCheckIn = criteria.checkIn() != null;
        boolean hasCheckOut = criteria.checkOut() != null;
        if (hasCheckIn != hasCheckOut) {
            throw new BadRequestException("INCOMPLETE_DATE_RANGE", "checkIn and checkOut must both be provided together");
        }
        if (hasCheckIn && !criteria.checkIn().isBefore(criteria.checkOut())) {
            throw new BadRequestException("INVALID_DATE_RANGE", "checkIn must be before checkOut");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("INVALID_PRICE_RANGE", "minPrice must not be greater than maxPrice");
        }
    }
}
