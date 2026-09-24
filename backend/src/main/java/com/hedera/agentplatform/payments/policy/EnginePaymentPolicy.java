package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyEngine;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.PolicyRequest;
import com.hedera.agentplatform.policies.repository.ApprovalRepository;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Connects Payments to the Policies module. Everything is the Policies module's: the engine's
 * verdict, the envelope balances ({@link EnvelopeLedger}), the recorded decision, the debit and the
 * approvals queue ({@link ApprovalService}). Payments only translates a payment into a {@link
 * PolicyRequest}.
 *
 * <p>Amounts are passed in the payment's smallest unit (tinybars, or the token's smallest unit), so
 * envelopes are counted in that same unit.
 */
@Component
public class EnginePaymentPolicy implements PaymentPolicy {

  private final EnvelopeLedger ledger;
  private final ApprovalService approvals;
  private final ApprovalRepository approvalRows;

  public EnginePaymentPolicy(
      EnvelopeLedger ledger, ApprovalService approvals, ApprovalRepository approvalRows) {
    this.ledger = ledger;
    this.approvals = approvals;
    this.approvalRows = approvalRows;
  }

  /** Pure: the engine on the current ledger. Nothing is recorded or debited. */
  @Override
  public PaymentPolicyDecision evaluate(PaymentEntity payment) {
    return map(PolicyEngine.decide(request(payment), ledger.state()));
  }

  /** Decided and recorded by the Policies module, which also debits on ALLOW and opens a HOLD. */
  @Override
  public Submitted submit(PaymentEntity payment) {
    ApprovalService.Submission s = approvals.submit(request(payment), ledger.state());
    return new Submitted(
        map(s.decision()), s.approval() == null ? null : s.approval().id(), s.auditEventId());
  }

  /**
   * The approval may already have been answered in the Policies module's own queue: approved there
   * means the payment may go; rejected there means it may not.
   */
  @Override
  public void approve(String approvalId) {
    String status = statusOf(approvalId);
    if ("APPROVED".equals(status)) {
      return;
    }
    if ("REJECTED".equals(status)) {
      throw new IllegalStateException(
          "Approval " + approvalId + " was rejected in the approvals queue");
    }
    // Re-checks affordability and debits the envelope; throws when it is no longer affordable.
    approvals.approve(approvalId);
  }

  @Override
  public void reject(String approvalId) {
    String status = statusOf(approvalId);
    if ("REJECTED".equals(status)) {
      return;
    }
    if ("APPROVED".equals(status)) {
      throw new IllegalStateException(
          "Approval " + approvalId + " was already approved in the approvals queue");
    }
    approvals.reject(approvalId);
  }

  private String statusOf(String approvalId) {
    return approvalRows
        .findById(approvalId)
        .map(a -> a.status)
        .orElseThrow(() -> new IllegalArgumentException("Unknown approval: " + approvalId));
  }

  private static PolicyRequest request(PaymentEntity payment) {
    return new PolicyRequest(envelope(payment.envelope), payment.amountUnits, payment.destination);
  }

  private static PaymentPolicyDecision map(PolicyDecision d) {
    return new PaymentPolicyDecision(Verdict.valueOf(d.verdict().name()), d.ruleId(), d.reason());
  }

  private static Envelope envelope(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return Envelope.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
