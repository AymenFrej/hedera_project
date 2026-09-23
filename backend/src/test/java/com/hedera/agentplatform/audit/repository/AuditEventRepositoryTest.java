package com.hedera.agentplatform.audit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AuditEventRepositoryTest {

    @Autowired private AuditEventRepository repository;

    @Test
    void persists_and_reads_back_an_audit_event() {
        AuditEventEntity event = new AuditEventEntity();
        event.id = "audit_test_1";
        event.agent = "AuditAgent";
        event.action = "TEST";
        event.status = "SUCCESS";
        event.createdAt = Instant.parse("2026-01-01T00:00:00Z");

        repository.save(event);

        assertThat(repository.findById("audit_test_1")).isPresent();
    }
}
