package com.hedera.agentplatform.payments.dto;

import java.time.Instant;

public record PaymentResponse(
    String id,
    String amount,
    String currency,
    String tokenId,
    String destination,
    String envelope,
    String memo,
    String status,
    String sourceAccount,
    String transactionId,
    String explorerUrl,
    String policyVerdict,
    String policyRuleId,
    String policyReason,
    String failureReason,
    String requestedByType,
    String requestedById,
    Instant createdAt,
    Instant updatedAt) {}
