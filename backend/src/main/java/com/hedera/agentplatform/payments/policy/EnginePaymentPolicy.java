package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyEngine;
import com.hedera.agentplatform.policies.PolicyRequest;
import org.springframework.stereotype.Component;

/**
 * Asks the Policies module's {@link PolicyEngine} about a payment. The decision is entirely the
 * engine's; the state it decides on comes from {@link PolicyStateProvider}.
 *
 * <p>Pure: it reads and decides, it records nothing. The payment preview relies on that, and
 * Payments writes the decision to the audit trail itself, with the payment id, once per payment.
 */
@Component
public class EnginePaymentPolicy implements PaymentPolicy {

  private final PolicyStateProvider states;

  public EnginePaymentPolicy(PolicyStateProvider states) {
    this.states = states;
  }

  @Override
  public PaymentPolicyDecision evaluate(PaymentEntity payment) {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(
                PolicyStateProvider.envelope(payment.envelope),
                payment.amountUnits,
                payment.destination),
            states.state(payment.currency));
    return new PaymentPolicyDecision(Verdict.valueOf(d.verdict().name()), d.ruleId(), d.reason());
  }
}
