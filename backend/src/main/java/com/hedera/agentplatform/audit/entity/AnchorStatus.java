package com.hedera.agentplatform.audit.entity;

/** Where an audit event stands with respect to the ledger. */
public enum AnchorStatus {
  /** Stored locally, not yet submitted to HCS. */
  PENDING,
  /** Submitted and acknowledged by the network, proof fields are populated. */
  ANCHORED,
  /** Submission failed; the local record is kept so the failure is visible. */
  FAILED
}
