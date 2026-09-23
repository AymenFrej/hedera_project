package com.hedera.agentplatform.policies.dto;

import java.time.Instant;

/** An approval request as an operator sees it. */
public record ApprovalResponse(
    String id,
    String taskId,
    String status,
    String ruleId,
    String reason,
    String envelope,
    Long amount,
    String counterparty,
    Instant requestedAt,
    Instant decidedAt,
    String decidedBy) {}
