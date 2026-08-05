package com.havyn.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.audit.service.AuditLogService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.service.PropertyService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminPropertyServiceTest {

    private final PropertyService propertyService = mock(PropertyService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AdminPropertyService service = new AdminPropertyService(propertyService, auditLogService);

    private final UUID adminId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(propertyService.getAny(propertyId)).thenReturn(property);
    }

    @Test
    void suspend_transitionsAndRecordsAnAuditEntryWithTheReason() {
        property.transitionTo(PropertyStatus.PENDING);
        property.transitionTo(PropertyStatus.ACTIVE);
        Property suspended = new Property(
                property.getHostId(), mock(PropertyType.class), "Sunset Villa", "d", "a", "c", "s", "co",
                BigDecimal.TEN, 4, 2, 2, BigDecimal.ONE);
        suspended.transitionTo(PropertyStatus.PENDING);
        suspended.transitionTo(PropertyStatus.ACTIVE);
        suspended.transitionTo(PropertyStatus.SUSPENDED);
        when(propertyService.transitionAsAdmin(propertyId, PropertyStatus.SUSPENDED)).thenReturn(suspended);

        Property result = service.suspend(adminId, propertyId, "Repeated guest complaints");

        assertThat(result.getStatus()).isEqualTo(PropertyStatus.SUSPENDED);
        verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(adminId), org.mockito.ArgumentMatchers.eq("PROPERTY_SUSPENDED"),
                org.mockito.ArgumentMatchers.eq("Property"), org.mockito.ArgumentMatchers.eq(propertyId), any(), any());
    }

    @Test
    void reject_transitionsPendingBackToDraft() {
        property.transitionTo(PropertyStatus.PENDING);
        Property rejected = new Property(
                property.getHostId(), mock(PropertyType.class), "Sunset Villa", "d", "a", "c", "s", "co",
                BigDecimal.TEN, 4, 2, 2, BigDecimal.ONE);
        when(propertyService.transitionAsAdmin(propertyId, PropertyStatus.DRAFT)).thenReturn(rejected);

        Property result = service.reject(adminId, propertyId, "Missing required amenity info");

        assertThat(result.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        verify(auditLogService).record(
                org.mockito.ArgumentMatchers.eq(adminId), org.mockito.ArgumentMatchers.eq("PROPERTY_REJECTED"),
                org.mockito.ArgumentMatchers.eq("Property"), org.mockito.ArgumentMatchers.eq(propertyId), any(), any());
    }
}
