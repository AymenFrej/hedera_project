package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;

/**
 * Decides whether a payment may run, must wait for a human, or is refused.
 *
 * <p>Payments owns the question, the Policies module owns the answer. This interface is the seam
 * between the two: once the policy engine is merged, an adapter bean implementing it replaces
 * {@link NoPolicyConfigured} automatically.
 */
public interface PaymentPolicy {

  PaymentPolicyDecision evaluate(PaymentEntity payment);

  enum Verdict {
    ALLOW,
    HOLD,
    DENY
  }

  /**
   * @param ruleId stable identifier of the rule that settled the decision, recorded in the audit
   *     trail
   * @param reason human-readable explanation shown in the UI
   */
  record PaymentPolicyDecision(Verdict verdict, String ruleId, String reason) {}
}
