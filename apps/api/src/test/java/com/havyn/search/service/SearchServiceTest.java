package com.havyn.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.common.error.BadRequestException;
import com.havyn.common.web.PageResponse;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.search.cache.CachedSearchPage;
import com.havyn.search.cache.SearchCacheService;
import com.havyn.search.repo.PropertySearchRepository;
import com.havyn.search.web.SearchResultItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SearchServiceTest {

    private final PropertySearchRepository propertySearchRepository = mock(PropertySearchRepository.class);
    private final SearchCacheService searchCacheService = mock(SearchCacheService.class);
    private final PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
    private final SearchService service =
            new SearchService(propertySearchRepository, searchCacheService, propertyMediaRepository);

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    void rejectsACheckInWithoutACheckOut() {
        SearchCriteria criteria = criteria(LocalDate.of(2026, 8, 1), null);

        assertThatThrownBy(() -> service.search(criteria, pageable))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INCOMPLETE_DATE_RANGE");
    }

    @Test
    void rejectsACheckOutWithoutACheckIn() {
        SearchCriteria criteria = criteria(null, LocalDate.of(2026, 8, 5));

        assertThatThrownBy(() -> service.search(criteria, pageable))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INCOMPLETE_DATE_RANGE");
    }

    @Test
    void rejectsACheckOutOnOrBeforeCheckIn() {
        SearchCriteria criteria = criteria(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5));

        assertThatThrownBy(() -> service.search(criteria, pageable))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void rejectsAMinPriceAboveMaxPrice() {
        SearchCriteria criteria = new SearchCriteria(
                null, null, null, null, BigDecimal.valueOf(500), BigDecimal.valueOf(100), null, null, null, null, null);

        assertThatThrownBy(() -> service.search(criteria, pageable))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_PRICE_RANGE");
    }

    @Test
    void returnsTheCachedPageWithoutQueryingTheRepositoryOnACacheHit() {
        SearchCriteria criteria = criteria(null, null);
        CachedSearchPage cached = new CachedSearchPage(List.of(), 0, 20, 0, null);
        when(searchCacheService.get(criteria, pageable)).thenReturn(Optional.of(cached));

        PageResponse<?> result = service.search(criteria, pageable);

        assertThat(result.total()).isZero();
        verify(propertySearchRepository, never()).search(any(), any());
    }

    @Test
    void queriesAndCachesOnACacheMiss() {
        SearchCriteria criteria = criteria(null, null);
        when(searchCacheService.get(criteria, pageable)).thenReturn(Optional.empty());
        Page<Property> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(propertySearchRepository.search(criteria, pageable)).thenReturn(emptyPage);

        service.search(criteria, pageable);

        verify(propertySearchRepository, times(1)).search(criteria, pageable);
        verify(searchCacheService, times(1)).put(any(), any(), any());
    }

    @Test
    void roundsCoordinatesToApproximateLocationBeforeCaching_launchChecklistFix() {
        SearchCriteria criteria = criteria(null, null);
        when(searchCacheService.get(criteria, pageable)).thenReturn(Optional.empty());
        PropertyType villa = mock(PropertyType.class);
        when(villa.getCode()).thenReturn("VILLA");
        Property property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(50000), 4, 2, 2, BigDecimal.valueOf(2));
        property.setLat(BigDecimal.valueOf(6.123456));
        property.setLng(BigDecimal.valueOf(3.987654));
        Page<Property> page = new PageImpl<>(List.of(property), pageable, 1);
        when(propertySearchRepository.search(criteria, pageable)).thenReturn(page);

        PageResponse<SearchResultItem> result = service.search(criteria, pageable);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).lat()).isEqualByComparingTo("6.12");
        assertThat(result.data().get(0).lng()).isEqualByComparingTo("3.99");
    }

    private SearchCriteria criteria(LocalDate checkIn, LocalDate checkOut) {
        return new SearchCriteria(null, checkIn, checkOut, null, null, null, null, null, null, null, null);
    }
}
