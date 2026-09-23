package com.hedera.agentplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.audit.service.AuditService;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The point of anchoring: a record altered in the database stops matching the ledger.
 *
 * <p>This is the demo scenario, run as a test so the demo cannot fail on stage. It writes a real
 * event to HCS, tampers with the local row the way an administrator with SQL access would, and
 * checks that verification turns red and says why.
 *
 * <p>Skipped unless HEDERA_OPERATOR_ID is set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
class AuditTamperDetectionLiveIT {

    @Autowired private AuditService auditService;
    @Autowired private AuditEventRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void a_record_altered_in_the_database_no_longer_matches_the_ledger() throws Exception {
        AuditEventEntity recorded =
                auditService.record(
                        "PaymentAgent", "TRANSFER", "SUCCESS", Map.of("amount", "50"));

        // 1. Honest state: the ledger and the database agree.
        VerificationResult before = awaitVerification(recorded.id);
        assertThat(before.verified()).as(before.detail()).isTrue();
        System.out.println("BEFORE_VERIFIED=" + before.verified());

        // 2. Tamper the way someone with database access would: change the amount, and recompute
        //    the stored hash so the record is internally consistent. Only the ledger disagrees.
        String tamperedPayload = recorded.payload.replace("\"amount\":\"50\"", "\"amount\":\"5000\"");
        assertThat(tamperedPayload).isNotEqualTo(recorded.payload);

        AuditEventEntity stored = repository.findById(recorded.id).orElseThrow();
        stored.payload = tamperedPayload;
        stored.payloadHash =
                com.hedera.agentplatform.audit.hedera.AuditPayload.sha256Hex(tamperedPayload);
        repository.saveAndFlush(stored);
        entityManager.clear();

        // 3. The ledger still holds the original message, so verification fails.
        VerificationResult after = auditService.verify(recorded.id);
        System.out.println("AFTER_VERIFIED=" + after.verified());
        System.out.println("AFTER_DETAIL=" + after.detail());

        assertThat(after.verified()).isFalse();
        assertThat(after.detail()).contains("does not match");
        assertThat(after.ledgerPayload())
                .as("the ledger should still show the original amount")
                .contains("\"amount\":\"50\"")
                .doesNotContain("5000");
    }

    /** Mirror nodes trail consensus by a second or two. */
    private VerificationResult awaitVerification(String id) throws InterruptedException {
        VerificationResult result = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            result = auditService.verify(id);
            if (result.verified()) {
                return result;
            }
            Thread.sleep(2000);
        }
        return result;
    }
}
