package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import java.util.List;
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
  @MockitoBean private PaymentPolicy policy;

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
  void unknown_payment_is_reported_as_a_bad_request() {
    assertThatThrownBy(() -> service.findById("pay_missing"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
