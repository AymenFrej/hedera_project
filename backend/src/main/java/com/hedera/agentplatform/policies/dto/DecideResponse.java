package com.hedera.agentplatform.policies.dto;

/**
 * What the platform answers. {@code approvalId} is present only for HOLD: it is the handle a human
 * uses to settle the request.
 */
public record DecideResponse(
    String verdict, String ruleId, String reason, Long balanceAfter, String approvalId) {}
