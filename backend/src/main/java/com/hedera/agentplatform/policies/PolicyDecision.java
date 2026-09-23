package com.hedera.agentplatform.policies;

/**
 * A settled decision. The stable {@code ruleId} is what makes the verdict auditable: it survives
 * rewording of the human-readable reason, and it is what gets anchored to HCS.
 */
public record PolicyDecision(
    Verdict verdict, String ruleId, String reason, PolicyRequest request, Long balanceAfter) {}
