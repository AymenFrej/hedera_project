package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;

/**
 * Decides whether a payment may run, must wait for a human, or is refused.
 *
 * <p>Payments owns the question, the Policies module owns the answer: the decision, where its
 * record lives, the approvals queue and the envelope balances. This interface is the seam between
 * the two; {@link EnginePaymentPolicy} connects it to the Policies module.
 */
public interface PaymentPolicy {

  /**
   * The verdict, and nothing else: no record, no approval, no debit. The payment preview calls it
   * on a payment that is never saved or sent.
   */
  PaymentPolicyDecision evaluate(PaymentEntity payment);

  /**
   * The verdict, recorded: this is the decision execution follows. The Policies module writes it
   * to the audit trail, debits the envelope on ALLOW, and opens an approval on HOLD.
   *
   * <p>The default only evaluates, for policies that keep no record.
   */
  default Submitted submit(PaymentEntity payment) {
    return new Submitted(evaluate(payment), null, null);
  }

  /**
   * Answers the approval behind a held payment with yes. Returns normally when the payment may now
   * be sent (including when it was already approved in the Policies module's queue); throws {@link
   * IllegalStateException} with the reason when it may not (e.g. no longer affordable, or already
   * rejected there).
   */
  default void approve(String approvalId) {}

  /** Answers the approval behind a held payment with no. */
  default void reject(String approvalId) {}

  enum Verdict {
    ALLOW,
    HOLD,
    DENY
  }

  /**
   * @param ruleId stable identifier of the rule that settled the decision
   * @param reason human-readable explanation shown in the UI
   */
  record PaymentPolicyDecision(Verdict verdict, String ruleId, String reason) {}

  /**
   * @param approvalId the approval opened for a HOLD; null otherwise
   * @param auditEventId the audit event the decision was recorded as; null when not recorded
   */
  record Submitted(PaymentPolicyDecision decision, String approvalId, String auditEventId) {}
}
