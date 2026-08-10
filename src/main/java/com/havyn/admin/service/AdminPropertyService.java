package com.havyn.admin.service;

import com.havyn.audit.service.AuditLogService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.service.PropertyService;
import com.havyn.properties.web.PropertyDetail;
import com.havyn.properties.web.PropertySummary;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listing moderation — see project-docs/prompts/18-admin-platform.md. Additive to,
 * not a replacement for, {@code properties.web.HostListingController}'s existing
 * self-publish flow — see {@code PropertyService#transitionAsAdmin}'s Javadoc for why.
 * Not built (a real, deliberate scope boundary, not an oversight): a hard
 * admin-approval gate replacing host self-publish entirely, which would mean editing
 * {@code properties/}'s own transition graph/controller, outside this prompt's file
 * scope (`admin/`, `audit/`) and a breaking change to an already-shipped capability.
 */
@Service
public class AdminPropertyService {

    private final PropertyService propertyService;
    private final AuditLogService auditLogService;

    public AdminPropertyService(PropertyService propertyService, AuditLogService auditLogService) {
        this.propertyService = propertyService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<Property> list(Pageable pageable) {
        return propertyService.listAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<PropertySummary> listSummaries(Pageable pageable) {
        return propertyService.listAll(pageable).map(PropertySummary::from);
    }

    @Transactional(readOnly = true)
    public Property get(UUID propertyId) {
        return propertyService.getAny(propertyId);
    }

    @Transactional(readOnly = true)
    public PropertyDetail getDetail(UUID propertyId) {
        return PropertyDetail.from(propertyService.getAny(propertyId));
    }

    @Transactional
    public PropertyDetail suspendDetail(UUID adminId, UUID propertyId, String reason) {
        return PropertyDetail.from(suspend(adminId, propertyId, reason));
    }

    @Transactional
    public PropertyDetail rejectDetail(UUID adminId, UUID propertyId, String reason) {
        return PropertyDetail.from(reject(adminId, propertyId, reason));
    }

    @Transactional
    public PropertyDetail reactivateDetail(UUID adminId, UUID propertyId) {
        return PropertyDetail.from(reactivate(adminId, propertyId));
    }

    /** ACTIVE (or any status the transition graph allows) -&gt; SUSPENDED — taking down a listing. */
    @Transactional
    public Property suspend(UUID adminId, UUID propertyId, String reason) {
        PropertyStatus before = propertyService.getAny(propertyId).getStatus();
        Property property = propertyService.transitionAsAdmin(propertyId, PropertyStatus.SUSPENDED);
        auditLogService.record(
                adminId, "PROPERTY_SUSPENDED", "Property", propertyId,
                Map.of("status", before.name()), Map.of("status", property.getStatus().name(), "reason", reason));
        return property;
    }

    /** PENDING -&gt; DRAFT — rejecting a submitted listing back to the host, with a reason. */
    @Transactional
    public Property reject(UUID adminId, UUID propertyId, String reason) {
        PropertyStatus before = propertyService.getAny(propertyId).getStatus();
        Property property = propertyService.transitionAsAdmin(propertyId, PropertyStatus.DRAFT);
        auditLogService.record(
                adminId, "PROPERTY_REJECTED", "Property", propertyId,
                Map.of("status", before.name()), Map.of("status", property.getStatus().name(), "reason", reason));
        return property;
    }

    /** SUSPENDED -&gt; ACTIVE — restoring a moderated listing to live/bookable status. */
    @Transactional
    public Property reactivate(UUID adminId, UUID propertyId) {
        PropertyStatus before = propertyService.getAny(propertyId).getStatus();
        Property property = propertyService.transitionAsAdmin(propertyId, PropertyStatus.ACTIVE);
        auditLogService.record(
                adminId, "PROPERTY_REACTIVATED", "Property", propertyId,
                Map.of("status", before.name()), Map.of("status", property.getStatus().name()));
        return property;
    }
}
