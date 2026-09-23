package com.hedera.agentplatform.shared.model;

import java.time.Instant;
import java.util.Map;

/**
 * One recorded action.
 *
 * <p>{@code actor} says who it is attributed to. It is resolved by the platform from the session,
 * never read from client input, and it is written into the message content rather than carried by
 * the transaction payer: the platform signs and pays for every audit message so the trail does not
 * depend on the audited party's willingness or balance.
 */
public record AuditEvent(
        String id,
        String agent,
        String action,
        String status,
        Instant createdAt,
        Actor actor,
        Map<String, Object> metadata) {}
