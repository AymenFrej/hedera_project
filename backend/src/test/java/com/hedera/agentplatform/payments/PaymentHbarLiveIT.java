package com.hedera.agentplatform.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.payments.HederaTestFixtures.CreatedAccount;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentReceipt;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.service.PaymentReceiptService;
import com.hedera.agentplatform.payments.service.PaymentService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The whole chain on the real testnet, with the real Policies module: policy → approval → Hedera →
 * Mirror Node → HCS audit, and a refusal that never reaches Hedera.
 *
 * <p>The Policies module's envelopes carry no unit (demo runway rent 500 / essentials 300 /
 * emergency 200), and HBAR amounts are counted in tinybars, so the amounts here are tiny: 0.000001
 * HBAR is 100 tinybars. Creates its own recipient each run.
 *
 * <pre>
 *   ./mvnw test -Dtest=PaymentHbarLiveIT
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentHbarLiveIT {

  @Autowired private PaymentService payments;
  @Autowired private PaymentReceiptService receipts;
  @Autowired private EnvelopeLedger ledger;
  @Autowired private Client client;

  private String recipient;

  @BeforeAll
  void freshRecipientAndLedger() throws Exception {
    ledger.reset();
    CreatedAccount account = HederaTestFixtures.createAccount(client, new Hbar(0));
    recipient = account.id().toString();
    System.out.println("LIVE_RECIPIENT=" + recipient);
  }

  private PaymentResponse pay(String hbar) {
    return payments.create(new CreatePaymentRequest(recipient, hbar, null, "ESSENTIALS", "PaymentHbarLiveIT"));
  }

  /** Mirror Node and HCS trail consensus by a few seconds: retry the receipt briefly. */
  private PaymentReceipt settledReceipt(String paymentId) throws InterruptedException {
    PaymentReceipt r = receipts.receipt(paymentId);
    for (int i = 0; i < 20 && !(r.badges().ledgerVerified() && r.badges().auditVerified()); i++) {
      Thread.sleep(3000);
      r = receipts.receipt(paymentId);
    }
    return r;
  }

  @Test
  @Order(1)
  void a_first_payment_is_held_approved_sent_and_proven_on_ledger_and_hcs() throws Exception {
    PaymentResponse held = pay("0.000001"); // 100 tinybars
    assertThat(held.policyRuleId()).isEqualTo("counterparty.unknown");
    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");

    PaymentResponse sent = payments.approve(held.id());
    assertThat(sent.status()).as(sent.failureReason()).isEqualTo("CONFIRMED");

    PaymentReceipt r = settledReceipt(held.id());
    assertThat(r.outcome()).isEqualTo("CONFIRMED");
    assertThat(r.badges().policyChecked()).isTrue();
    assertThat(r.badges().ledgerVerified()).isTrue();
    assertThat(r.badges().auditVerified()).as("every audit event read back from HCS").isTrue();
    assertThat(r.timeline())
        .extracting("label")
        .contains("Policy evaluated: HOLD", "Approved by a reviewer", "Transfer confirmed by Hedera");
    System.out.println("LIVE_EXPLORER=" + sent.explorerUrl());
  }

  @Test
  @Order(2)
  void a_known_recipient_within_the_envelope_is_allowed_and_sent() {
    PaymentResponse p = pay("0.0000005"); // 50 tinybars, 200 left in essentials

    assertThat(p.policyVerdict()).isEqualTo("ALLOW");
    assertThat(p.status()).as(p.failureReason()).isEqualTo("CONFIRMED");
  }

  @Test
  @Order(3)
  void more_than_the_envelope_holds_is_blocked_before_reaching_hedera() {
    PaymentResponse p = pay("0.00001"); // 1000 tinybars, 150 left

    assertThat(p.policyRuleId()).isEqualTo("funds.insufficient");
    assertThat(p.status()).isEqualTo("REJECTED");
    assertThat(p.transactionId()).as("no Hedera transaction").isNull();
    assertThat(p.policyExplanation().shortfall()).isNotNull();
  }
}
