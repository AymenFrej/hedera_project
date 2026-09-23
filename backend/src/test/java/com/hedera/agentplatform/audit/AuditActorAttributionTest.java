package com.hedera.agentplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.ActorType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribution is decided server-side. Without authentication everything is attributed to the
 * platform rather than to an invented user: writing a false actor to an immutable ledger would be
 * worse than writing none.
 */
@SpringBootTest
@Transactional
class AuditActorAttributionTest {

    @Autowired private AuditService auditService;

    @Test
    void without_authentication_events_are_attributed_to_the_platform() {
        AuditEventEntity recorded =
                auditService.record("PaymentAgent", "TRANSFER", "SUCCESS", Map.of());

        assertThat(recorded.actorType).isEqualTo(ActorType.SYSTEM.name());
        assertThat(recorded.actorId).isEqualTo("platform");
        assertThat(recorded.actorHederaAccountId)
                .as("no user account should be invented")
                .isNull();
    }

    @Test
    void an_explicit_actor_is_stored_and_written_into_the_payload() {
        AuditEventEntity recorded =
                auditService.record(
                        "PaymentAgent",
                        "TRANSFER",
                        "SUCCESS",
                        Actor.user("user_42", "0.0.777"),
                        Map.of("amount", "50"));

        assertThat(recorded.actorType).isEqualTo(ActorType.USER.name());
        assertThat(recorded.actorId).isEqualTo("user_42");
        assertThat(recorded.actorHederaAccountId).isEqualTo("0.0.777");
        assertThat(recorded.payload).contains("user_42").contains("0.0.777");
    }
}
