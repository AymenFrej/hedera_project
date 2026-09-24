package com.hedera.agentplatform.policies.dto;

import java.util.Map;

/**
 * What the platform answers. {@code approvalId} is present only for HOLD: it is the handle a human
 * uses to settle the request. {@code auditEventId} is the record of the decision itself, so the
 * verdict can be opened on the audit trail instead of being taken on trust; {@code anchored} says
 * whether that record actually reached the ledger. {@code envelopes} carries the budget as it
 * stands after the decision, so a screen cannot show a stale balance beside a fresh verdict.
 */
public record DecideResponse(
    String verdict,
    String ruleId,
    String reason,
    Long balanceAfter,
    String approvalId,
    String auditEventId,
    boolean anchored,
    Map<String, Long> envelopes) {}
