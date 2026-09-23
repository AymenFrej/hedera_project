package com.hedera.agentplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.entity.AnchorStatus;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.audit.service.AuditService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end check against the real Hedera testnet: record an event, anchor it to HCS, then read it
 * back from the Mirror Node and confirm the hashes match.
 *
 * <p>Skipped automatically unless HEDERA_OPERATOR_ID is present, so it never breaks the build for
 * teammates without a testnet account. Run it with:
 *
 * <pre>
 *   HEDERA_OPERATOR_ID=0.0.x HEDERA_OPERATOR_PRIVATE_KEY=0x... ./mvnw test -Dtest=AuditAnchorLiveIT
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
class AuditAnchorLiveIT {

  @Autowired private AuditService auditService;

  @Test
  void event_is_anchored_to_hcs_and_verified_through_the_mirror_node() throws Exception {
    assertThat(auditService.isLedgerActive())
        .as("credentials are set, the live gateway should be in use")
        .isTrue();

    AuditEventEntity recorded =
        auditService.record(
            "AuditAgent", "INTEGRATION_TEST", "SUCCESS", Map.of("source", "AuditAnchorLiveIT"));

    assertThat(recorded.anchorStatus).isEqualTo(AnchorStatus.ANCHORED.name());
    assertThat(recorded.topicId).isNotBlank();
    assertThat(recorded.transactionId).isNotBlank();
    assertThat(recorded.consensusTimestamp).isNotBlank();
    assertThat(recorded.sequenceNumber).isNotNull();

    // Mirror nodes trail consensus by a second or two; retry briefly rather than sleeping once.
    VerificationResult result = null;
    for (int attempt = 0; attempt < 10; attempt++) {
      result = auditService.verify(recorded.id);
      if (result.verified()) {
        break;
      }
      Thread.sleep(2000);
    }

    assertThat(result).isNotNull();
    assertThat(result.verified()).as(result.detail()).isTrue();
    assertThat(result.ledgerPayload()).isEqualTo(recorded.payload);
    assertThat(result.explorerUrl()).contains(recorded.topicId);

    System.out.println("LIVE_TOPIC=" + recorded.topicId);
    System.out.println("LIVE_TX=" + recorded.transactionId);
    System.out.println("LIVE_SEQ=" + recorded.sequenceNumber);
    System.out.println("LIVE_CONSENSUS=" + recorded.consensusTimestamp);
    System.out.println("LIVE_EXPLORER=" + result.explorerUrl());
  }
}
