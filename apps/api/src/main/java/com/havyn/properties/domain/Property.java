package com.havyn.properties.domain;

import com.havyn.amenities.domain.Amenity;
import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A host's listing. {@code hostId} references {@code app_user} directly — there is no
 * {@code HostProfile} entity yet (project-docs/database/01-data-model.md's ERD sketches
 * one, but it isn't in prompt 10's file scope: {@code hosts/} isn't a "MAY modify"
 * path). See backend/02-domain-modules.md's properties section for the deviation note.
 */
@Entity
@Table(name = "property")
public class Property extends BaseEntity {

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private PropertyType type;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "lat")
    private BigDecimal lat;

    @Column(name = "lng")
    private BigDecimal lng;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "NGN";

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "bedrooms", nullable = false)
    private int bedrooms;

    @Column(name = "beds", nullable = false)
    private int beds;

    @Column(name = "bathrooms", nullable = false)
    private BigDecimal bathrooms;

    @Column(name = "cleaning_fee", nullable = false)
    private BigDecimal cleaningFee = BigDecimal.ZERO;

    @Column(name = "service_fee_pct", nullable = false)
    private BigDecimal serviceFeePct = BigDecimal.ZERO;

    @Column(name = "house_rules")
    private String houseRules;

    @Column(name = "cancellation_policy", nullable = false, length = 40)
    private String cancellationPolicy = "FLEXIBLE";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(name = "rating_avg", nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "property_amenity",
            joinColumns = @JoinColumn(name = "property_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new LinkedHashSet<>();

    /**
     * Null for every host-created listing; populated only for rows imported from an
     * external partner (e.g. {@code "RAYPROP"}) — see V13__rayprop_import.sql and
     * {@code properties.rayprop.RayPropSyncService}, which upserts on this pair to
     * keep re-syncs idempotent.
     */
    @Column(name = "external_source", length = 30)
    private String externalSource;

    @Column(name = "external_id", length = 100)
    private String externalId;

    protected Property() {
        // JPA
    }

    public Property(UUID hostId, PropertyType type, String title, String description, String address, String city,
            String state, String country, BigDecimal basePrice, int capacity, int bedrooms, int beds,
            BigDecimal bathrooms) {
        this.hostId = hostId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
        this.basePrice = basePrice;
        this.capacity = capacity;
        this.bedrooms = bedrooms;
        this.beds = beds;
        this.bathrooms = bathrooms;
    }

    public UUID getHostId() {
        return hostId;
    }

    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(int bedrooms) {
        this.bedrooms = bedrooms;
    }

    public int getBeds() {
        return beds;
    }

    public void setBeds(int beds) {
        this.beds = beds;
    }

    public BigDecimal getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(BigDecimal bathrooms) {
        this.bathrooms = bathrooms;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = cleaningFee;
    }

    public BigDecimal getServiceFeePct() {
        return serviceFeePct;
    }

    public void setServiceFeePct(BigDecimal serviceFeePct) {
        this.serviceFeePct = serviceFeePct;
    }

    public String getHouseRules() {
        return houseRules;
    }

    public void setHouseRules(String houseRules) {
        this.houseRules = houseRules;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public void setCancellationPolicy(String cancellationPolicy) {
        this.cancellationPolicy = cancellationPolicy;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public void transitionTo(PropertyStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition property from " + status + " to " + target);
        }
        this.status = target;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    /** Called by {@code reviews/} after each new review — recomputed from source (AVG/COUNT), never incremented. */
    public void applyAggregateRating(BigDecimal ratingAvg, int ratingCount) {
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
    }

    public Set<Amenity> getAmenities() {
        return amenities;
    }

    public void setAmenities(Set<Amenity> amenities) {
        this.amenities = amenities;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalReference(String externalSource, String externalId) {
        this.externalSource = externalSource;
        this.externalId = externalId;
    }
}
