package com.hedera.agentplatform.payments.dto;

import java.util.List;

/**
 * What would happen if this payment were executed now. Nothing is recorded, sent or audited.
 *
 * <p>Informational only: executing sends the request through the normal path, which asks the
 * policy again. A preview never authorizes anything.
 *
 * @param outcome BLOCKED (policy DENY: no Hedera transaction would be created), LIKELY_TO_FAIL
 *     (a ledger fact says Hedera would refuse it, and the network fee would still be charged),
 *     NEEDS_APPROVAL (policy HOLD), READY, or SIMULATION (no Hedera credentials)
 * @param balanceBefore balance of the asset on the paying account, as a decimal; null when unknown
 * @param balanceAfter {@code balanceBefore} minus the amount; for HBAR the network fee comes on top
 */
public record PaymentPreview(
    String outcome,
    String summary,
    String payerAccount,
    String destination,
    String amount,
    long amountUnits,
    String tokenId,
    String symbol,
    String balanceBefore,
    String balanceAfter,
    Policy policy,
    List<Check> checks,
    String note) {

  /**
   * Exactly what the policy returned, plus the envelope numbers it decided on.
   *
   * @param available what the envelope holds now, as a person reads it; null without an envelope
   * @param shortfall how much more than {@code available} is asked; null when it fits
   */
  public record Policy(
      String verdict, String ruleId, String reason, String available, String shortfall) {}

  /**
   * A fact read from the ledger.
   *
   * @param status PASS, WARN (may fail), FAIL (Hedera would refuse), or UNKNOWN (could not check)
   */
  public record Check(String name, String status, String detail) {}
}
