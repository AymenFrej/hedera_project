package com.hedera.agentplatform.payments.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The verdicts are the engine's; what is tested here is the state Payments feeds it: envelope
 * balances from the runway minus committed payments, and the accounts already paid.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "payments.runways=0.0.7777=1000")
class EnginePaymentPolicyTest {

  private static final String TOKEN = "0.0.7777";
  private static final String KNOWN = "0.0.100";
  private static final String NEW = "0.0.200";

  @Autowired private EnginePaymentPolicy policy;
  @Autowired private PaymentRepository payments;

  private PaymentEntity payment(String destination, long units, String envelope, String status) {
    PaymentEntity p = new PaymentEntity();
    p.id = "pay_" + UUID.randomUUID();
    p.destination = destination;
    p.amount = BigDecimal.valueOf(units);
    p.amountUnits = units;
    p.currency = TOKEN;
    p.tokenId = TOKEN;
    p.envelope = envelope;
    p.status = status;
    p.createdAt = Instant.now();
    p.updatedAt = p.createdAt;
    return p;
  }

  private PaymentPolicyDecision decide(String destination, long units, String envelope) {
    return policy.evaluate(payment(destination, units, envelope, "PENDING"));
  }

  private void alreadyPaid(String destination, long units, String envelope) {
    payments.saveAndFlush(payment(destination, units, envelope, "CONFIRMED"));
  }

  @Test
  void the_runway_is_split_by_the_engine_and_spent_payments_reduce_their_envelope() {
    // 1000 -> rent 500, essentials 300, emergency 200
    alreadyPaid(KNOWN, 100, "ESSENTIALS");

    assertThat(policy.state(TOKEN).balances())
        .containsEntry(Envelope.RENT, 500L)
        .containsEntry(Envelope.ESSENTIALS, 200L)
        .containsEntry(Envelope.EMERGENCY, 200L);
  }

  @Test
  void a_payment_to_a_known_account_within_its_envelope_is_allowed() {
    alreadyPaid(KNOWN, 10, "ESSENTIALS");

    PaymentPolicyDecision d = decide(KNOWN, 50, "essentials");

    assertThat(d.verdict()).isEqualTo(Verdict.ALLOW);
    assertThat(d.ruleId()).isEqualTo("policy.ok");
  }

  @Test
  void a_first_payment_to_an_account_is_held() {
    alreadyPaid(KNOWN, 10, "ESSENTIALS");

    assertThat(decide(NEW, 10, "ESSENTIALS").ruleId()).isEqualTo("counterparty.unknown");
  }

  @Test
  void more_than_the_envelope_holds_is_denied() {
    alreadyPaid(KNOWN, 250, "ESSENTIALS");

    PaymentPolicyDecision d = decide(KNOWN, 100, "ESSENTIALS");

    assertThat(d.verdict()).isEqualTo(Verdict.DENY);
    assertThat(d.ruleId()).isEqualTo("funds.insufficient");
  }

  @Test
  void a_payment_without_an_envelope_is_denied_by_the_engine() {
    assertThat(decide(KNOWN, 10, null).ruleId()).isEqualTo("envelope.unknown");
  }

  @Test
  void an_asset_without_a_runway_has_no_funded_envelope() {
    PaymentEntity hbar = payment(KNOWN, 10, "RENT", "PENDING");
    hbar.currency = "HBAR";
    hbar.tokenId = null;

    assertThat(policy.evaluate(hbar).ruleId()).isEqualTo("envelope.unfunded");
  }

  @Test
  void held_and_rejected_payments_do_not_use_up_an_envelope() {
    payments.saveAndFlush(payment(KNOWN, 300, "ESSENTIALS", "AWAITING_APPROVAL"));
    payments.saveAndFlush(payment(KNOWN, 300, "ESSENTIALS", "REJECTED"));

    assertThat(policy.state(TOKEN).balances()).containsEntry(Envelope.ESSENTIALS, 300L);
  }

  @Test
  void runway_configuration_is_validated() {
    assertThat(EnginePaymentPolicy.parseRunways("hbar=5, 0.0.1=10"))
        .containsEntry("HBAR", 5L)
        .containsEntry("0.0.1", 10L);
    assertThatThrownBy(() -> EnginePaymentPolicy.parseRunways("HBAR"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EnginePaymentPolicy.parseRunways("HBAR=0"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
