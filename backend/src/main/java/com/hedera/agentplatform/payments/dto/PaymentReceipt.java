package com.hedera.agentplatform.payments.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything the result screen shows about one payment, each part from something that happened.
 *
 * @param outcome CONFIRMED, FAILED (sent, Hedera refused or it never reached consensus), BLOCKED
 *     (policy DENY: never sent), REJECTED (a reviewer refused it: never sent), AWAITING_APPROVAL,
 *     IN_PROGRESS (sent, no final result yet), SIMULATED (no Hedera credentials)
 * @param onLedger true when the Mirror Node has the transaction, successful or not
 * @param ledger the Mirror Node comparison; null when no transaction was ever created
 * @param timeline what happened, in order: the payment record, then each audit event
 * @param audit the audit events for this payment and, when anchored, their HCS verification
 * @param badges only facts a backend check just confirmed
 */
public record PaymentReceipt(
    PaymentResponse payment,
    String outcome,
    String headline,
    String detail,
    boolean onLedger,
    PaymentVerification ledger,
    List<Step> timeline,
    List<AuditProof> audit,
    Badges badges,
    List<SafetyRow> safety,
    String network) {

  /**
   * One line of the transaction safety summary, from a fact the backend holds or just checked.
   *
   * @param state PASS, FAIL, WAITING, NOT_APPLICABLE (the step was never reached or does not apply)
   *     or UNKNOWN (could not be checked)
   */
  public record SafetyRow(String name, String state, String detail) {}

  /**
   * @param state DONE, FAILED, WAITING, or NOT_CREATED (something that deliberately did not happen)
   * @param auditEventId the audit event behind this step; null for the payment record itself and
   *     for facts derived from it (e.g. "no Hedera transaction was created")
   */
  public record Step(String label, String state, Instant at, String detail, String auditEventId) {}

  /**
   * @param anchorStatus ANCHORED, PENDING (not on HCS, e.g. simulation) or FAILED
   * @param verified true only when the HCS message was read back and its hash matched; null when
   *     the event was never anchored
   */
  public record AuditProof(
      String id,
      String action,
      String status,
      Instant createdAt,
      String anchorStatus,
      String topicId,
      Long sequenceNumber,
      Boolean verified,
      String verificationDetail,
      String explorerUrl) {}

  /**
   * @param policyChecked a policy decision was recorded (a real engine, not {@code policy.none})
   * @param ledgerVerified the Mirror Node transaction matched the payment
   * @param auditVerified every audit event of this payment was read back from HCS and matched
   */
  public record Badges(boolean policyChecked, boolean ledgerVerified, boolean auditVerified) {}
}
