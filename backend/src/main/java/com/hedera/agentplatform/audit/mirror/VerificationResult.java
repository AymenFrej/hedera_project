package com.hedera.agentplatform.audit.mirror;

/**
 * Result of comparing a locally stored audit event with what the ledger actually holds.
 *
 * <p>Both hashes are exposed, not just the verdict: when they differ, showing them side by side is
 * what makes the failure understandable rather than an unexplained red cross.
 *
 * @param verified true only when the Mirror Node returned a message whose content matches the
 *     stored payload hash
 * @param detail human readable reason, always populated
 * @param storedHash SHA-256 of the payload held in our database
 * @param ledgerHash SHA-256 of the message actually found on the ledger, null when none was found
 * @param storedPayload the payload held in our database, null when the event was never anchored
 * @param ledgerPayload what the Mirror Node returned, null when nothing was found
 * @param consensusTimestamp consensus timestamp as recorded by the network
 * @param explorerUrl HashScan link so a reviewer can check independently
 */
public record VerificationResult(
        boolean verified,
        String detail,
        String storedHash,
        String ledgerHash,
        String storedPayload,
        String ledgerPayload,
        String consensusTimestamp,
        String explorerUrl) {

    public static VerificationResult notAnchored() {
        return new VerificationResult(
                false,
                "Event was never submitted to the ledger",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static VerificationResult failure(String detail, String storedHash, String explorerUrl) {
        return new VerificationResult(
                false, detail, storedHash, null, null, null, null, explorerUrl);
    }
}
