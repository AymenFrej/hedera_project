package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.shared.model.AuditEvent;

/**
 * Writes audit events to the ledger.
 *
 * <p>The implementation in use depends on configuration: {@link MockHederaAuditGateway} when no
 * operator credentials are set, {@link HcsHederaAuditGateway} otherwise.
 */
public interface HederaAuditGateway {

  /** Publishes the event and returns the proof the network gave back. */
  AnchorReceipt publish(AuditEvent event);

  /** True when events are really submitted to Hedera. */
  boolean isLive();
}
