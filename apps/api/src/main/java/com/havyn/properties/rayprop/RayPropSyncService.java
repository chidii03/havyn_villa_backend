package com.havyn.properties.rayprop;

import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.repo.PropertyTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps {@link RayPropListing} onto our own {@code property}/{@code property_media}
 * tables and upserts by {@code (external_source, external_id)} — see
 * V13__rayprop_import.sql. Triggered on-demand via {@code POST
 * /api/v1/admin/rayprop/sync} (see {@code AdminRayPropController}) rather than a
 * scheduled job, so the first import can be verified before automating it.
 *
 * <p>A sync that returns far fewer than RayProp's advertised "5,000+ verified
 * listings" is expected, not a bug — {@link RayPropSyncResult#stoppedEarlyDueToQuota()}
 * says whether this run stopped because RayProp's inventory was exhausted
 * ({@code false}) or because the account hit its daily unique-listing quota
 * ({@code true}, 500/day on the sandbox key this integration currently uses — see
 * {@link RayPropClient}'s class doc for the full evidence trail).
 */
@Service
public class RayPropSyncService {

    private static final Logger log = LoggerFactory.getLogger(RayPropSyncService.class);
    private static final String SOURCE = "RAYPROP";
    private static final String COUNTRY = "Nigeria"; // RayProp is Nigeria-only (NGN, Nigerian bank codes throughout its docs).
    private static final List<String> DISPLAY_TYPE_CODES =
            List.of("APARTMENT", "CABIN", "CONDO", "GUESTHOUSE", "HOUSE", "SHORTLET", "STUDIO");

    /** Seeded by V13__rayprop_import.sql — the {@code app_user} row every RayProp-imported listing attaches to as its {@code host_id}. */
    private static final UUID SYSTEM_HOST_ID = UUID.fromString("a11a11a1-1a11-4a11-a11a-11a11a11a11a");

    private final RayPropClient client;
    private final PropertyRepository propertyRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final PropertyMediaRepository propertyMediaRepository;

    public RayPropSyncService(
            RayPropClient client,
            PropertyRepository propertyRepository,
            PropertyTypeRepository propertyTypeRepository,
            PropertyMediaRepository propertyMediaRepository) {
        this.client = client;
        this.propertyRepository = propertyRepository;
        this.propertyTypeRepository = propertyTypeRepository;
        this.propertyMediaRepository = propertyMediaRepository;
    }

    @Transactional
    public RayPropSyncResult sync() {
        Map<String, PropertyType> propertyTypes = loadDisplayPropertyTypes();

        RayPropFetchResult fetchResult = client.fetchAllListings();
        List<RayPropListing> listings = fetchResult.listings();
        int created = 0;
        int updated = 0;

        for (RayPropListing listing : listings) {
            Optional<Property> existing = propertyRepository.findByExternalSourceAndExternalId(SOURCE, listing.id());
            Property property;
            PropertyType displayType = displayTypeFor(listing, propertyTypes);

            if (existing.isPresent()) {
                property = existing.get();
                applyFields(property, listing, displayType);
                updated++;
            } else {
                property = new Property(
                        SYSTEM_HOST_ID,
                        displayType,
                        truncate(orPlaceholder(listing.title(), "Untitled shortlet"), 150),
                        orPlaceholder(listing.description(), "No description provided."),
                        addressFrom(listing),
                        orPlaceholder(listing.city(), "Lagos"),
                        orPlaceholder(listing.state(), "Lagos"),
                        COUNTRY,
                        priceInNaira(listing),
                        Math.max(listing.maxGuests(), 1),
                        Math.max(listing.bedrooms(), 0),
                        Math.max(listing.bedrooms(), 0), // RayProp doesn't expose a separate "beds" count — bedrooms is the closest real signal.
                        bathroomsOrDefault(listing));
                property.setExternalReference(SOURCE, listing.id());
                property.setLat(listing.lat());
                property.setLng(listing.lng());
                // A fresh import is "verified inventory" per RayProp's own docs — publish
                // straight through DRAFT -> PENDING -> ACTIVE rather than leaving it in
                // a state a host would normally still need to submit/publish.
                property.transitionTo(PropertyStatus.PENDING);
                property.transitionTo(PropertyStatus.ACTIVE);
                created++;
            }

            Property saved = propertyRepository.save(property);
            syncMedia(saved.getId(), listing.imageUrls());
        }

        RayPropDataAccess dataAccess = fetchResult.lastDataAccess();
        log.info(
                "RayProp sync complete: fetched={} created={} updated={} pagesFetched={} stoppedEarlyDueToQuota={}",
                listings.size(), created, updated, fetchResult.pagesFetched(), fetchResult.stoppedEarlyDueToQuota());
        return new RayPropSyncResult(
                listings.size(),
                created,
                updated,
                fetchResult.pagesFetched(),
                fetchResult.stoppedEarlyDueToQuota(),
                dataAccess != null ? dataAccess.accessedToday() : null,
                dataAccess != null ? dataAccess.dailyLimit() : null);
    }

    private void applyFields(Property property, RayPropListing listing, PropertyType displayType) {
        property.setType(displayType);
        property.setTitle(truncate(orPlaceholder(listing.title(), property.getTitle()), 150));
        property.setDescription(orPlaceholder(listing.description(), property.getDescription()));
        property.setAddress(addressFrom(listing));
        property.setCity(orPlaceholder(listing.city(), property.getCity()));
        property.setState(orPlaceholder(listing.state(), property.getState()));
        property.setCountry(COUNTRY);
        property.setLat(listing.lat());
        property.setLng(listing.lng());
        property.setCurrency(listing.currency());
        property.setBasePrice(priceInNaira(listing));
        property.setCapacity(Math.max(listing.maxGuests(), 1));
        property.setBedrooms(Math.max(listing.bedrooms(), 0));
        property.setBeds(Math.max(listing.bedrooms(), 0));
        property.setBathrooms(bathroomsOrDefault(listing));
    }

    private Map<String, PropertyType> loadDisplayPropertyTypes() {
        Map<String, PropertyType> propertyTypes = new LinkedHashMap<>();
        for (String code : DISPLAY_TYPE_CODES) {
            propertyTypes.put(code, propertyTypeRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> new IllegalStateException(code + " property type is not seeded")));
        }
        return propertyTypes;
    }

    private static PropertyType displayTypeFor(RayPropListing listing, Map<String, PropertyType> propertyTypes) {
        String haystack = String.join(" ",
                        orPlaceholder(listing.category(), ""),
                        orPlaceholder(listing.title(), ""),
                        orPlaceholder(listing.description(), ""))
                .toLowerCase(Locale.ROOT);

        String matchedCode = null;
        if (haystack.contains("studio") || listing.bedrooms() == 0) {
            matchedCode = "STUDIO";
        } else if (haystack.contains("cabin") || haystack.contains("chalet")) {
            matchedCode = "CABIN";
        } else if (haystack.contains("condo") || haystack.contains("condominium")) {
            matchedCode = "CONDO";
        } else if (haystack.contains("guesthouse") || haystack.contains("guest house")) {
            matchedCode = "GUESTHOUSE";
        } else if (haystack.contains("duplex")
                || haystack.contains("terrace")
                || haystack.contains("house")
                || haystack.contains("home")) {
            matchedCode = "HOUSE";
        } else if (haystack.contains("apartment") || haystack.contains("flat")) {
            matchedCode = "APARTMENT";
        } else if (haystack.contains("shortlet") || haystack.contains("short let")) {
            matchedCode = "SHORTLET";
        }

        if (matchedCode == null) {
            int index = Math.floorMod(listing.id().hashCode(), DISPLAY_TYPE_CODES.size());
            matchedCode = DISPLAY_TYPE_CODES.get(index);
        }
        return propertyTypes.get(matchedCode);
    }

    private void syncMedia(UUID propertyId, List<String> imageUrls) {
        // Simplest correct way to keep media in sync with the source on every re-run:
        // clear and re-insert rather than diffing — RayProp doesn't give us a stable
        // per-image id to diff against, only a URL list.
        propertyMediaRepository.deleteAllByPropertyId(propertyId);
        propertyMediaRepository.flush();
        int position = 0;
        for (String url : normalizedUniqueImageUrls(imageUrls)) {
            propertyMediaRepository.save(new PropertyMedia(
                    propertyId,
                    url,
                    SOURCE.toLowerCase() + ":" + propertyId + ":" + position,
                    MediaResourceType.IMAGE,
                    extensionOf(url),
                    null, // width unknown — RayProp doesn't expose image dimensions
                    null, // height unknown
                    null, // not video
                    0L, // bytes unknown — RayProp doesn't expose file size at listing time, and a HEAD-per-image round trip isn't worth it for a Phase-1 import
                    position,
                    null));
            position++;
        }
    }

    private static List<String> normalizedUniqueImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String imageUrl : imageUrls) {
            if (imageUrl == null) {
                continue;
            }
            String value = imageUrl.trim();
            if (value.startsWith("https://") || value.startsWith("http://")) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static String addressFrom(RayPropListing listing) {
        // RayProp never gives a street address (guest/host PII is only released after
        // a real booking, per its webhooks docs) — this is the most specific location
        // string available pre-booking.
        String neighborhood = listing.neighborhood();
        String city = orPlaceholder(listing.city(), "Lagos");
        return (neighborhood == null || neighborhood.isBlank()) ? city : neighborhood + ", " + city;
    }

    private static BigDecimal priceInNaira(RayPropListing listing) {
        return BigDecimal.valueOf(listing.pricePerNightMinorUnits(), 2).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bathroomsOrDefault(RayPropListing listing) {
        BigDecimal bathrooms = listing.bathrooms();
        return (bathrooms == null || bathrooms.signum() <= 0) ? BigDecimal.ONE : bathrooms;
    }

    private static String extensionOf(String url) {
        int dot = url.lastIndexOf('.');
        int query = url.indexOf('?', dot);
        if (dot < 0) {
            return "jpg";
        }
        String ext = query > dot ? url.substring(dot + 1, query) : url.substring(dot + 1);
        return ext.isBlank() || ext.length() > 10 ? "jpg" : ext.toLowerCase();
    }

    private static String orPlaceholder(String value, String placeholder) {
        return (value == null || value.isBlank()) ? placeholder : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
