package com.hedera.agentplatform.audit.hedera;

/** Raised when an audit event could not be written to the ledger. */
public class AuditAnchorException extends RuntimeException {
  public AuditAnchorException(String message, Throwable cause) {
    super(message, cause);
  }
}
