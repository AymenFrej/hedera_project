package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentReceipt;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.Step;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.dto.ApprovalResponse;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments against the real Policies module: its engine, its envelope ledger (seeded rent 500 ℏ,
 * essentials 300 ℏ, emergency 200 ℏ, all held in tinybars) and its approvals queue. No Hedera credentials in tests, so an
 * allowed payment ends SIMULATED.
 */
@SpringBootTest
@Transactional
class PaymentPolicyIntegrationTest {

  private static final String RECIPIENT = "0.0.4242";

  @Autowired private PaymentService payments;

  @Autowired private PaymentReceiptService receipts;
  @Autowired private ApprovalService approvals;
  @Autowired private EnvelopeLedger ledger;

  /** Token decimals without asking the real Mirror Node. */
  @MockitoBean private TokenDecimals decimals;

  @BeforeEach
  void tokenDecimals() {
    when(decimals.of(null)).thenReturn(TokenDecimals.HBAR);
    when(decimals.of(anyString())).thenReturn(0);
  }

  private PaymentResponse pay(long amount, String envelope) {
    return payments.create(
        new CreatePaymentRequest(RECIPIENT, String.valueOf(amount), "0.0.7777", envelope, null));
  }

  private long essentials() {
    return ledger.state().balances().get(Envelope.ESSENTIALS);
  }

  private List<String> labels(String paymentId) {
    return receipts.receipt(paymentId).timeline().stream().map(Step::label).toList();
  }

  /** A first payment to an account is held; approving it vouches for the account. */
  private void vouchForRecipient() {
    payments.approve(pay(1, "ESSENTIALS").id());
  }

  @Test
  void the_decision_is_recorded_by_the_policies_module_and_shows_in_the_timeline() {
    PaymentResponse denied = pay(10, null);

    assertThat(denied.status()).isEqualTo("REJECTED");
    assertThat(denied.policyRuleId()).isEqualTo("envelope.unknown");
    assertThat(denied.policyAuditEventId()).as("recorded by the Policies module").isNotNull();
    PaymentReceipt r = receipts.receipt(denied.id());
    assertThat(r.audit()).extracting("action").containsExactly("POLICY_DECISION");
    assertThat(r.outcome()).isEqualTo("BLOCKED");
  }

  @Test
  void a_refusal_is_explained_with_the_numbers_it_was_decided_on() {
    vouchForRecipient(); // essentials 300 ℏ, less the 1 tinybar that vouched for the recipient
    long left = essentials();
    long more_than_the_envelope_holds = left + 1;

    PaymentResponse denied = pay(more_than_the_envelope_holds, "ESSENTIALS");

    assertThat(denied.policyRuleId()).isEqualTo("funds.insufficient");
    var why = denied.policyExplanation();
    assertThat(why.requested()).isEqualTo(String.valueOf(more_than_the_envelope_holds));
    assertThat(why.available()).isEqualTo(String.valueOf(left));
    assertThat(why.shortfall()).isEqualTo("1");
    assertThat(why.transactionCreated()).isFalse();
    assertThat(essentials()).as("a refusal spends nothing").isEqualTo(left);
  }

  @Test
  void a_first_payment_is_held_in_the_policies_approvals_queue() {
    PaymentResponse held = pay(10, "ESSENTIALS");

    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(held.policyRuleId()).isEqualTo("counterparty.unknown");
    assertThat(approvals.pending()).extracting(ApprovalResponse::id).contains(held.approvalId());
  }

  @Test
  void approving_in_payments_answers_the_queue_debits_the_envelope_and_sends() {
    long before = essentials();
    PaymentResponse held = pay(10, "ESSENTIALS");

    PaymentResponse sent = payments.approve(held.id());

    assertThat(sent.status()).isEqualTo("SIMULATED");
    assertThat(approvals.pending()).extracting(ApprovalResponse::id).doesNotContain(held.approvalId());
    assertThat(essentials()).isEqualTo(before - 10);
    assertThat(labels(held.id()))
        .contains("Policy evaluated: HOLD", "Approved by a reviewer", "Transfer simulated");
    assertThat(receipts.receipt(held.id()).safety())
        .filteredOn(r -> r.name().equals("Policy"))
        .singleElement()
        .satisfies(r -> {
          assertThat(r.state()).isEqualTo("PASS");
          assertThat(r.detail()).contains("then approved by a reviewer");
        });
  }

  @Test
  void an_allowed_payment_is_debited_from_its_envelope() {
    vouchForRecipient();
    long before = essentials();

    PaymentResponse allowed = pay(20, "ESSENTIALS");

    assertThat(allowed.policyVerdict()).isEqualTo("ALLOW");
    assertThat(essentials()).isEqualTo(before - 20);
  }

  @Test
  void a_payment_approved_in_the_policies_queue_is_sent_when_approved_in_payments() {
    PaymentResponse held = pay(10, "ESSENTIALS");
    approvals.approve(held.approvalId());

    assertThat(payments.approve(held.id()).status()).isEqualTo("SIMULATED");
  }

  @Test
  void a_payment_rejected_in_the_policies_queue_cannot_be_sent() {
    PaymentResponse held = pay(10, "ESSENTIALS");
    approvals.reject(held.approvalId());

    PaymentResponse after = payments.approve(held.id());

    assertThat(after.status()).isEqualTo("REJECTED");
    assertThat(after.failureReason()).contains("rejected in the approvals queue");
    assertThat(after.transactionId()).isNull();
  }

  @Test
  void rejecting_in_payments_answers_the_queue() {
    PaymentResponse held = pay(10, "ESSENTIALS");

    assertThat(payments.reject(held.id()).status()).isEqualTo("REJECTED");
    assertThat(approvals.pending()).extracting(ApprovalResponse::id).doesNotContain(held.approvalId());
    assertThat(labels(held.id())).contains("Rejected by a reviewer");
  }
}
