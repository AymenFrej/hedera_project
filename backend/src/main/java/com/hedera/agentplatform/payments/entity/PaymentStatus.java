package com.hedera.agentplatform.payments.entity;

/**
 * Lifecycle of a payment.
 *
 * <pre>
 * PENDING ──policy──► REJECTED            (DENY, or a human rejected it)
 *    │
 *    ├──────────────► AWAITING_APPROVAL ──approve──► SUBMITTED
 *    │                                                  │
 *    └──ALLOW───────────────────────────────────────► SUBMITTED ──► CONFIRMED | FAILED | SIMULATED
 * </pre>
 *
 * <p>{@code SIMULATED} is what the mock gateway produces: nothing reached Hedera, and the UI must
 * not present it as a confirmed transfer.
 */
public enum PaymentStatus {
  PENDING,
  AWAITING_APPROVAL,
  REJECTED,
  SUBMITTED,
  CONFIRMED,
  FAILED,
  SIMULATED
}
