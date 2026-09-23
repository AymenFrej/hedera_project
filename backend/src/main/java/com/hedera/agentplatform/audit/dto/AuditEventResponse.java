package com.hedera.agentplatform.audit.dto;

import java.time.Instant;

/**
 * An audit event as exposed by the API, including the ledger proof when it has one.
 *
 * <p>{@code anchorStatus} tells the caller whether the ledger fields mean anything: PENDING means
 * the event exists only locally, ANCHORED means it was accepted by the network, FAILED means the
 * submission was attempted and did not work.
 */
public record AuditEventResponse(
    String id,
    String agent,
    String action,
    String status,
    Instant createdAt,
    String anchorStatus,
    String topicId,
    String transactionId,
    String consensusTimestamp,
    Long sequenceNumber,
    String payloadHash) {}
