package com.havyn.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.havyn.audit.domain.AuditLog;
import com.havyn.audit.repo.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLogServiceTest {

    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuditLogService service = new AuditLogService(auditLogRepository, objectMapper);

    @Test
    void record_serializesBeforeAndAfterSnapshotsAsJson() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(actorId, "PROPERTY_SUSPENDED", "Property", targetId, Map.of("status", "ACTIVE"), Map.of("status", "SUSPENDED"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo("PROPERTY_SUSPENDED");
        assertThat(saved.getTargetType()).isEqualTo("Property");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getBefore()).contains("\"status\":\"ACTIVE\"");
        assertThat(saved.getAfter()).contains("\"status\":\"SUSPENDED\"");
    }

    @Test
    void record_handlesNullBeforeAndAfter() {
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(UUID.randomUUID(), "PLATFORM_SETTING_UPDATED", "PlatformSetting", null, null, Map.of("value", "15"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getBefore()).isNull();
        assertThat(captor.getValue().getTargetId()).isNull();
    }
}
