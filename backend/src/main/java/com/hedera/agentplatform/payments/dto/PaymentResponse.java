package com.hedera.agentplatform.payments.dto;

import java.time.Instant;

public record PaymentResponse(
    String id,
    String amount,
    String currency,
    String assetSymbol,
    String tokenId,
    String destination,
    String envelope,
    String memo,
    String keepAtLeast,
    String status,
    String sourceAccount,
    String transactionId,
    String explorerUrl,
    String policyVerdict,
    String policyRuleId,
    String policyReason,
    PolicyExplanation policyExplanation,
    String failureReason,
    String approvalId,
    String policyAuditEventId,
    String requestedByType,
    String requestedById,
    Instant createdAt,
    Instant updatedAt,
    /** PAYMENT, or TOP_UP (treasury HBAR to the requester's own wallet). */
    String kind) {}
