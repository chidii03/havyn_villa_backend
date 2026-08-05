package com.havyn.properties.rayprop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.repo.PropertyTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RayPropSyncService#sync()} verified against a mocked {@link RayPropClient} —
 * the actual HTTP/pagination/quota behavior is {@code RayPropClientTest}'s job; this
 * covers the create-vs-update mapping and that {@link RayPropSyncResult} correctly
 * carries a partial (quota-stopped) fetch through rather than discarding it.
 */
class RayPropSyncServiceTest {

    private final RayPropClient client = mock(RayPropClient.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final PropertyTypeRepository propertyTypeRepository = mock(PropertyTypeRepository.class);
    private final PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);

    private final RayPropSyncService service =
            new RayPropSyncService(client, propertyRepository, propertyTypeRepository, propertyMediaRepository);

    private PropertyType shortlet;
    private PropertyType apartment;

    @BeforeEach
    void setUp() {
        shortlet = mock(PropertyType.class);
        when(shortlet.getCode()).thenReturn("SHORTLET");
        apartment = mock(PropertyType.class);
        when(apartment.getCode()).thenReturn("APARTMENT");
        for (String code : List.of("CABIN", "CONDO", "GUESTHOUSE", "HOUSE", "STUDIO")) {
            PropertyType type = mock(PropertyType.class);
            when(type.getCode()).thenReturn(code);
            when(propertyTypeRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(type));
        }
        when(propertyTypeRepository.findByCodeIgnoreCase("APARTMENT")).thenReturn(Optional.of(apartment));
        when(propertyTypeRepository.findByCodeIgnoreCase("SHORTLET")).thenReturn(Optional.of(shortlet));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsANewPropertyForAListingNeverSeenBefore() {
        RayPropListing listing = new RayPropListing(
                "rp_lst_new", "New Shortlet", "Description", "NGN", 4, 2, BigDecimal.valueOf(2), "Lagos", "Lagos",
                "Lekki", "shortlet", 6_000_000L, List.of("https://images.rayprop.io/1.jpg"));
        when(propertyRepository.findByExternalSourceAndExternalId("RAYPROP", "rp_lst_new")).thenReturn(Optional.empty());
        when(client.fetchAllListings())
                .thenReturn(new RayPropFetchResult(List.of(listing), 1, false, new RayPropDataAccess(1, 500, 499)));

        RayPropSyncResult result = service.sync();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        verify(propertyRepository).save(any(Property.class));
    }

    @Test
    void updatesAnExistingPropertyInsteadOfCreatingADuplicate() {
        Property existing = new Property(
                UUID.fromString("a11a11a1-1a11-4a11-a11a-11a11a11a11a"), shortlet, "Old Title", "Old description",
                "Lekki, Lagos", "Lagos", "Lagos", "Nigeria", BigDecimal.valueOf(50000), 2, 1, 1, BigDecimal.ONE);
        RayPropListing listing = new RayPropListing(
                "rp_lst_existing", "Updated Title", "Updated description", "NGN", 4, 2, BigDecimal.valueOf(2),
                "Lagos", "Lagos", "Lekki", "apartment", 7_500_000L, List.of());
        when(propertyRepository.findByExternalSourceAndExternalId("RAYPROP", "rp_lst_existing"))
                .thenReturn(Optional.of(existing));
        when(client.fetchAllListings())
                .thenReturn(new RayPropFetchResult(List.of(listing), 1, false, null));

        RayPropSyncResult result = service.sync();

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("Updated Title");
        assertThat(existing.getBasePrice()).isEqualByComparingTo("75000.00");
        assertThat(existing.getType()).isSameAs(apartment);
    }

    /**
     * The specific scenario the RayProp audit was about: a fetch that stopped early
     * because the daily quota was hit must still commit whatever it did fetch, and the
     * result must say so — not silently look like "RayProp only has 1 listing."
     */
    @Test
    void aQuotaStoppedFetchStillSyncsWhatWasFetched_andReportsWhyItStopped() {
        RayPropListing listing = new RayPropListing(
                "rp_lst_1", "Title", "Description", "NGN", 2, 1, BigDecimal.ONE, "Lagos", "Lagos", "Ikeja",
                "shortlet", 4_000_000L, List.of());
        when(propertyRepository.findByExternalSourceAndExternalId("RAYPROP", "rp_lst_1")).thenReturn(Optional.empty());
        when(client.fetchAllListings())
                .thenReturn(new RayPropFetchResult(List.of(listing), 10, true, new RayPropDataAccess(500, 500, 0)));

        RayPropSyncResult result = service.sync();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.pagesFetched()).isEqualTo(10);
        assertThat(result.stoppedEarlyDueToQuota()).isTrue();
        assertThat(result.dailyQuotaUsed()).isEqualTo(500);
        assertThat(result.dailyQuotaLimit()).isEqualTo(500);
    }

    @Test
    void anEmptyFetch_syncsNothingAndReportsZeros() {
        when(client.fetchAllListings()).thenReturn(new RayPropFetchResult(List.of(), 1, false, null));

        RayPropSyncResult result = service.sync();

        assertThat(result.fetched()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.dailyQuotaUsed()).isNull();
        assertThat(result.dailyQuotaLimit()).isNull();
        verify(propertyRepository, never()).save(any());
    }
}
