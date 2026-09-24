package com.hedera.agentplatform.payments.dto;

import java.util.List;

/**
 * What the Mirror Node says about a payment, compared field by field with what we stored.
 *
 * <p>Each check is exposed rather than only the verdict, so the UI can show which part matched and
 * which did not instead of an unexplained red cross.
 *
 * @param verified true only when the Mirror Node has the transaction and every check passed
 * @param detail human-readable summary, always populated
 * @param paymentStatus status of the payment after this check (it may have been recovered)
 * @param ledgerResult result reported by the network, e.g. SUCCESS; null when not found
 */
public record PaymentVerification(
    boolean verified,
    String detail,
    String paymentStatus,
    String transactionId,
    String ledgerResult,
    String consensusTimestamp,
    List<Check> checks,
    String explorerUrl) {

  /**
   * @param name what is compared, e.g. "Recipient received"
   * @param expected value from our record
   * @param actual value found on the ledger
   */
  public record Check(String name, String expected, String actual, boolean ok) {}
}
