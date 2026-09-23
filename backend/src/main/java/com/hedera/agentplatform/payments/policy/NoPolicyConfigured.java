package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;

/**
 * Fallback used until the Policies module provides a {@link PaymentPolicy} bean.
 *
 * <p>It allows every payment, but says so: the rule id {@code policy.none} is stored on the payment
 * and written to the audit trail, so nobody can mistake "no policy" for "policy passed".
 */
public class NoPolicyConfigured implements PaymentPolicy {

  @Override
  public PaymentPolicyDecision evaluate(PaymentEntity payment) {
    return new PaymentPolicyDecision(
        Verdict.ALLOW, "policy.none", "no policy engine connected yet: payment not checked");
  }
}
