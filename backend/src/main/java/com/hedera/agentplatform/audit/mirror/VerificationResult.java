package com.hedera.agentplatform.audit.mirror;

/**
 * Result of comparing a locally stored audit event with what the ledger actually holds.
 *
 * @param verified true only when the Mirror Node returned a message whose content matches the
 *     stored payload hash
 * @param detail human readable reason, always populated
 * @param ledgerPayload what the Mirror Node returned, null when nothing was found
 * @param consensusTimestamp consensus timestamp as recorded by the network
 * @param explorerUrl HashScan link so a reviewer can check independently
 */
public record VerificationResult(
    boolean verified,
    String detail,
    String ledgerPayload,
    String consensusTimestamp,
    String explorerUrl) {

  public static VerificationResult notAnchored() {
    return new VerificationResult(
        false, "Event was never submitted to the ledger", null, null, null);
  }

  public static VerificationResult failure(String detail, String explorerUrl) {
    return new VerificationResult(false, detail, null, null, explorerUrl);
  }
}
