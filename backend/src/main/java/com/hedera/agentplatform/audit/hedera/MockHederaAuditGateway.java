package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.shared.model.AuditEvent;

/**
 * Used when no Hedera operator credentials are configured, so the application still runs for
 * teammates without a testnet account. Returns a receipt that is explicitly not a proof: every
 * ledger field is null and {@link #isLive()} is false.
 */
public class MockHederaAuditGateway implements HederaAuditGateway {

  @Override
  public AnchorReceipt publish(AuditEvent event) {
    String payload = AuditPayload.canonicalJson(event);
    return new AnchorReceipt(null, null, null, null, payload, AuditPayload.sha256Hex(payload));
  }

  @Override
  public boolean isLive() {
    return false;
  }
}
