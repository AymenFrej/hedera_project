package com.hedera.agentplatform.payments.dto;

/**
 * Why the policy decided what it decided, with the numbers it decided on. Every field comes from
 * the decision itself or from the ledger at that moment; no limit is invented here.
 *
 * @param requested the amount asked for, as a person reads it
 * @param available what the envelope held when the policy decided; null when there was no envelope
 * @param shortfall how much more than {@code available} was asked; null when it fitted
 * @param transactionCreated false when nothing was ever sent to Hedera
 */
public record PolicyExplanation(
    String verdict,
    String ruleId,
    String reason,
    String envelope,
    String asset,
    String requested,
    String available,
    String shortfall,
    boolean transactionCreated) {}
