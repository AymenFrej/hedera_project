package com.hedera.agentplatform.audit.hedera;

/**
 * Proof returned by the network after submitting an audit event.
 *
 * @param topicId HCS topic the message landed in
 * @param transactionId Hedera transaction id, e.g. {@code 0.0.1234@1790177410.527000569}
 * @param consensusTimestamp consensus time assigned by the network
 * @param sequenceNumber position of the message inside the topic
 * @param payload exact string submitted, kept for comparison with the ledger
 * @param payloadHash SHA-256 of the payload, hex encoded
 */
public record AnchorReceipt(
    String topicId,
    String transactionId,
    String consensusTimestamp,
    Long sequenceNumber,
    String payload,
    String payloadHash) {}
