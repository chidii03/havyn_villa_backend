package com.havyn.media.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single Cloudinary asset attached to a listing — URL/metadata only, per ADR-005;
 * see project-docs/prompts/14-media-storage.md. {@code propertyId} is a plain UUID
 * (not a JPA association) — same reasoning as {@code Booking.propertyId}: this module
 * lives in {@code media/}, not {@code properties/}.
 */
@Entity
@Table(name = "property_media")
public class PropertyMedia extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "secure_url", nullable = false)
    private String secureUrl;

    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 10)
    private MediaResourceType resourceType;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration")
    private BigDecimal duration;

    @Column(name = "bytes", nullable = false)
    private long bytes;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "alt")
    private String alt;

    protected PropertyMedia() {
        // JPA
    }

    public PropertyMedia(
            UUID propertyId,
            String secureUrl,
            String publicId,
            MediaResourceType resourceType,
            String format,
            Integer width,
            Integer height,
            BigDecimal duration,
            long bytes,
            int position,
            String alt) {
        this.propertyId = propertyId;
        this.secureUrl = secureUrl;
        this.publicId = publicId;
        this.resourceType = resourceType;
        this.format = format;
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.bytes = bytes;
        this.position = position;
        this.alt = alt;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getSecureUrl() {
        return secureUrl;
    }

    public String getPublicId() {
        return publicId;
    }

    public MediaResourceType getResourceType() {
        return resourceType;
    }

    public String getFormat() {
        return format;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public BigDecimal getDuration() {
        return duration;
    }

    public long getBytes() {
        return bytes;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getAlt() {
        return alt;
    }
}
