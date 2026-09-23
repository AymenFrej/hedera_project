package com.hedera.agentplatform.audit.hedera;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.shared.model.AuditEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockHederaAuditGatewayTest {

  @Test
  void mock_never_pretends_to_have_anchored_anything() {
    MockHederaAuditGateway gateway = new MockHederaAuditGateway();

    AnchorReceipt receipt =
        gateway.publish(
            new AuditEvent(
                "audit_1",
                "AuditAgent",
                "TEST",
                "SUCCESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of()));

    assertThat(gateway.isLive()).isFalse();
    assertThat(receipt.topicId()).isNull();
    assertThat(receipt.transactionId()).isNull();
    assertThat(receipt.consensusTimestamp()).isNull();
    assertThat(receipt.sequenceNumber()).isNull();
    // The payload and its hash are still computed, so the local record stays consistent.
    assertThat(receipt.payload()).contains("audit_1");
    assertThat(receipt.payloadHash()).hasSize(64);
  }
}
