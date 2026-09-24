package com.hedera.agentplatform.payments.service;

import org.mockito.Answers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs without Hedera credentials, so the mock gateway is in place: an allowed payment must end up
 * SIMULATED, never CONFIRMED.
 */
@SpringBootTest
@Transactional
class PaymentServiceTest {

  @Autowired private PaymentService service;
  @Autowired private AuditEventRepository auditEvents;
  @Autowired private PaymentRepository payments;
  @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private PaymentPolicy policy;

  /** Token decimals without asking the real Mirror Node. */
  @MockitoBean private TokenDecimals decimals;

  @BeforeEach
  void tokenDecimals() {
    when(decimals.of(null)).thenReturn(TokenDecimals.HBAR);
    when(decimals.of(anyString())).thenReturn(0);
  }

  private static CreatePaymentRequest hbar(String amount) {
    return new CreatePaymentRequest("0.0.4242", amount, null, "essentials", "rent share");
  }

  private void policyAnswers(Verdict verdict, String ruleId) {
    when(policy.evaluate(any())).thenReturn(new PaymentPolicyDecision(verdict, ruleId, "because"));
  }

  @Test
  void an_allowed_payment_is_sent_and_marked_simulated_without_credentials() {
    policyAnswers(Verdict.ALLOW, "policy.ok");

    PaymentResponse payment = service.create(hbar("12.5"));

    assertThat(payment.status()).isEqualTo("SIMULATED");
    assertThat(payment.currency()).isEqualTo("HBAR");
    assertThat(payment.envelope()).isEqualTo("ESSENTIALS");
    assertThat(payment.policyRuleId()).isEqualTo("policy.ok");
    assertThat(payment.transactionId()).as("nothing reached Hedera").isNull();
  }

  @Test
  void a_held_payment_waits_and_is_sent_only_after_approval() {
    policyAnswers(Verdict.HOLD, "counterparty.unknown");

    PaymentResponse held = service.create(hbar("5"));
    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");

    PaymentResponse approved = service.approve(held.id());
    assertThat(approved.status()).isEqualTo("SIMULATED");
  }

  @Test
  void a_held_payment_can_be_rejected_and_then_cannot_be_approved() {
    policyAnswers(Verdict.HOLD, "amount.large");

    PaymentResponse held = service.create(hbar("5"));
    PaymentResponse rejected = service.reject(held.id());

    assertThat(rejected.status()).isEqualTo("REJECTED");
    assertThatThrownBy(() -> service.approve(held.id()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void a_denied_payment_is_never_sent_and_cannot_be_approved() {
    policyAnswers(Verdict.DENY, "funds.insufficient");

    PaymentResponse denied = service.create(hbar("5"));

    assertThat(denied.status()).isEqualTo("REJECTED");
    assertThat(denied.policyRuleId()).isEqualTo("funds.insufficient");
    assertThatThrownBy(() -> service.approve(denied.id()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void every_decision_and_transfer_is_written_to_the_audit_trail() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    long before = auditEvents.count();

    service.create(hbar("1"));

    List<String> actions =
        auditEvents.findAll().stream()
            .filter(e -> "PaymentAgent".equals(e.agent))
            .map(e -> e.action + ":" + e.status)
            .toList();
    assertThat(auditEvents.count() - before).isEqualTo(2);
    assertThat(actions).contains("PAYMENT_POLICY:ALLOW", "TRANSFER:SUCCESS");
  }

  @Test
  void a_token_payment_uses_the_token_id_as_currency() {
    policyAnswers(Verdict.ALLOW, "policy.ok");

    PaymentResponse payment =
        service.create(new CreatePaymentRequest("0.0.4242", "500", "0.0.7777", null, null));

    assertThat(payment.currency()).isEqualTo("0.0.7777");
    assertThat(payment.tokenId()).isEqualTo("0.0.7777");
    assertThat(payment.status()).isEqualTo("SIMULATED");
  }

  @Test
  void a_token_amount_is_converted_with_the_token_decimals() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    when(decimals.of("0.0.2222")).thenReturn(2);

    PaymentResponse payment =
        service.create(new CreatePaymentRequest("0.0.4242", "5.25", "0.0.2222", null, null));

    assertThat(payments.findById(payment.id()).orElseThrow().amountUnits).isEqualTo(525L);
    assertThat(payment.amount()).isEqualTo("5.25");
  }

  @Test
  void the_same_idempotency_key_twice_pays_once() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    long paymentsBefore = payments.count();

    PaymentResponse first = service.create(hbar("3"), "form-4f1c2a9e");
    PaymentResponse retry = service.create(hbar("3"), "form-4f1c2a9e");

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(payments.count() - paymentsBefore).isEqualTo(1);
  }

  @Test
  void an_idempotency_key_reused_for_a_different_payment_is_refused() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    service.create(hbar("3"), "form-77aa01bc");

    assertThatThrownBy(() -> service.create(hbar("4"), "form-77aa01bc"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("different payment");
  }

  @Test
  void a_malformed_idempotency_key_is_refused() {
    assertThatThrownBy(() -> service.create(hbar("3"), "bad key!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void amounts_are_shown_without_trailing_zeros() {
    policyAnswers(Verdict.ALLOW, "policy.ok");

    assertThat(service.create(hbar("12.50")).amount()).isEqualTo("12.5");
  }

  @Test
  void unknown_payment_is_reported_as_a_bad_request() {
    assertThatThrownBy(() -> service.findById("pay_missing"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
