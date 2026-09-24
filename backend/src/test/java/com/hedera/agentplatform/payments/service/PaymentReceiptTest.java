package com.hedera.agentplatform.payments.service;

import org.mockito.Answers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentReceipt;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.Step;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.Outcome;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorTransaction;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** The result screen shows what happened, and only that, for each fundamentally different path. */
@SpringBootTest
@Transactional
class PaymentReceiptTest {

  private static final String PAYER = "0.0.5239440";
  private static final String RECIPIENT = "0.0.10682427";
  private static final String TX = PAYER + "@1790194819.526000707";

  @Autowired private PaymentService payments;
  @Autowired private PaymentReceiptService receipts;
  @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private PaymentPolicy policy;
  @MockitoBean private HederaPaymentGateway gateway;
  @MockitoBean private PaymentMirrorClient mirror;

  @BeforeEach
  void liveGateway() {
    when(gateway.isLive()).thenReturn(true);
    when(gateway.newTransactionId()).thenReturn(TX);
  }

  private void policyAnswers(Verdict verdict, String ruleId) {
    when(policy.evaluate(any())).thenReturn(new PaymentPolicyDecision(verdict, ruleId, "because"));
  }

  private void hederaAnswers(Outcome outcome, String status) {
    when(gateway.transferHbar(anyString(), anyString(), anyLong(), any()))
        .thenReturn(new PaymentResult(outcome, TX, status, PAYER, false));
  }

  private void ledgerHas(String result, long received) {
    when(mirror.findTransaction(anyString()))
        .thenReturn(
            MirrorLookup.found(
                new MirrorTransaction(
                    result, "CRYPTOTRANSFER", "1790194854.872086514",
                    Map.of(RECIPIENT, received, PAYER, -(received + 128_158L)), List.of())));
  }

  private String pay() {
    return payments.create(new CreatePaymentRequest(RECIPIENT, "0.01", null, null, null)).id();
  }

  private static List<String> labels(PaymentReceipt r) {
    return r.timeline().stream().map(Step::label).toList();
  }

  @Test
  void confirmed_payment_shows_the_real_steps_and_a_verified_ledger() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    hederaAnswers(Outcome.SUCCESS, "SUCCESS");
    ledgerHas("SUCCESS", 1_000_000L);

    PaymentReceipt r = receipts.receipt(pay());

    assertThat(r.outcome()).isEqualTo("CONFIRMED");
    assertThat(labels(r))
        .containsExactly(
            "Payment requested", "Policy evaluated: ALLOW", "Transfer confirmed by Hedera");
    assertThat(r.onLedger()).isTrue();
    assertThat(r.badges().policyChecked()).isTrue();
    assertThat(r.badges().ledgerVerified()).isTrue();
    assertThat(r.payment().explorerUrl()).contains(TX);
    // Two audit events, each with its step pointing at it.
    assertThat(r.audit()).hasSize(2);
    assertThat(r.timeline()).filteredOn(s -> s.auditEventId() != null).hasSize(2);
  }

  @Test
  void failed_payment_shows_the_refusal_and_no_success_badge() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    hederaAnswers(Outcome.FAILED, "INVALID_ACCOUNT_ID");
    ledgerHas("INVALID_ACCOUNT_ID", 0L);

    PaymentReceipt r = receipts.receipt(pay());

    assertThat(r.outcome()).isEqualTo("FAILED");
    assertThat(r.detail()).contains("INVALID_ACCOUNT_ID").contains("fee");
    assertThat(labels(r)).contains("Transfer refused by Hedera");
    assertThat(r.ledger().verified()).as("the ledger agrees it failed").isTrue();
    assertThat(r.badges().ledgerVerified()).as("no success badge for a failure").isFalse();
  }

  @Test
  void blocked_payment_never_reaches_hedera_and_says_so() {
    policyAnswers(Verdict.DENY, "funds.insufficient");

    PaymentReceipt r = receipts.receipt(pay());

    assertThat(r.outcome()).isEqualTo("BLOCKED");
    assertThat(labels(r))
        .containsExactly(
            "Payment requested", "Policy evaluated: DENY", "No Hedera transaction was created");
    assertThat(labels(r)).noneMatch(l -> l.startsWith("Transfer"));
    assertThat(r.ledger()).isNull();
    assertThat(r.onLedger()).isFalse();
    assertThat(r.payment().transactionId()).isNull();
    assertThat(r.payment().explorerUrl()).isNull();
    assertThat(r.payment().policyRuleId()).isEqualTo("funds.insufficient");
  }

  @Test
  void a_transfer_without_receipt_is_in_progress_not_failed() {
    policyAnswers(Verdict.ALLOW, "policy.ok");
    hederaAnswers(Outcome.UNKNOWN, "TimeoutException");
    when(mirror.findTransaction(anyString())).thenReturn(MirrorLookup.notFound());

    PaymentReceipt r = receipts.receipt(pay());

    assertThat(r.outcome()).isEqualTo("IN_PROGRESS");
    assertThat(r.headline()).isEqualTo("Awaiting ledger confirmation");
    assertThat(r.timeline()).last().extracting(Step::state).isEqualTo("WAITING");
  }

  @Test
  void a_simulated_payment_never_claims_hedera_confirmed_it() {
    when(gateway.isLive()).thenReturn(false);
    when(gateway.newTransactionId()).thenReturn(null);
    policyAnswers(Verdict.ALLOW, "policy.ok");
    when(gateway.transferHbar(any(), anyString(), anyLong(), any()))
        .thenReturn(new PaymentResult(Outcome.SUCCESS, null, "SIMULATED", null, true));

    PaymentReceipt r = receipts.receipt(pay());

    assertThat(r.outcome()).isEqualTo("SIMULATED");
    assertThat(labels(r)).contains("Transfer simulated").doesNotContain("Transfer confirmed by Hedera");
    assertThat(r.audit()).allSatisfy(a -> assertThat(a.verified()).isNull());
    assertThat(r.badges().auditVerified()).isFalse();
  }

  @Test
  void no_policy_engine_means_no_policy_badge() {
    policyAnswers(Verdict.ALLOW, "policy.none");
    hederaAnswers(Outcome.SUCCESS, "SUCCESS");
    ledgerHas("SUCCESS", 1_000_000L);

    assertThat(receipts.receipt(pay()).badges().policyChecked()).isFalse();
  }

  @Test
  void an_audit_event_of_another_payment_cannot_be_verified_through_this_one() {
    policyAnswers(Verdict.DENY, "rule");
    String first = pay();
    String second = pay();
    String eventOfSecond = receipts.receipt(second).audit().get(0).id();

    assertThatThrownBy(() -> receipts.verifyAuditEvent(first, eventOfSecond))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
