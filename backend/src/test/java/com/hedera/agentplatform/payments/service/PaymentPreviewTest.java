package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentPreview;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.Outcome;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountInfo;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenInfo;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenLookup;
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

@SpringBootTest
@Transactional
class PaymentPreviewTest {

  private static final String PAYER = "0.0.5239440";
  private static final String RECIPIENT = "0.0.10687139";
  private static final String TOKEN = "0.0.10687138";

  @Autowired private PaymentPreviewService previews;
  @Autowired private PaymentService payments;
  @Autowired private PaymentRepository paymentRows;
  @Autowired private AuditEventRepository auditRows;
  @MockitoBean private PaymentPolicy policy;
  @MockitoBean private HederaPaymentGateway gateway;
  @MockitoBean private PaymentMirrorClient mirror;

  @BeforeEach
  void liveLedger() {
    when(gateway.payerAccount()).thenReturn(PAYER);
    when(gateway.isLive()).thenReturn(true);
    when(mirror.findAccount(anyString()))
        .thenReturn(new AccountLookup(LookupState.FOUND, new AccountInfo(false, 0)));
    when(mirror.findToken(TOKEN))
        .thenReturn(new TokenLookup(LookupState.FOUND, new TokenInfo(TOKEN, "PAYTEST", "Demo", 0)));
    when(mirror.findAssociation(anyString(), anyString())).thenReturn(LookupState.FOUND);
    holds(1_000_000_000L, 100L);
    policyAnswers(Verdict.ALLOW);
  }

  private void holds(long tinybars, long paytest) {
    when(mirror.findBalances(PAYER))
        .thenReturn(
            new BalanceLookup(
                LookupState.FOUND,
                new AccountBalances(
                    PAYER,
                    tinybars,
                    "1790195836.839746104",
                    List.of(new TokenHolding(TOKEN, "PAYTEST", "Demo", 0, paytest)))));
  }

  private void policyAnswers(Verdict verdict) {
    when(policy.evaluate(any()))
        .thenReturn(new PaymentPolicyDecision(verdict, "rule." + verdict, "because " + verdict));
  }

  private static CreatePaymentRequest paytest(long amount) {
    return new CreatePaymentRequest(RECIPIENT, String.valueOf(amount), TOKEN, null, null);
  }

  @Test
  void an_allowed_payment_with_passing_checks_is_ready_and_shows_balances() {
    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("READY");
    assertThat(p.symbol()).isEqualTo("PAYTEST");
    assertThat(p.balanceBefore()).isEqualTo("100");
    assertThat(p.balanceAfter()).isEqualTo("75");
    assertThat(p.checks()).extracting("status").containsOnly("PASS");
  }

  @Test
  void a_preview_records_sends_and_audits_nothing() {
    long paymentsBefore = paymentRows.count();
    long auditBefore = auditRows.count();

    previews.preview(paytest(25));

    assertThat(paymentRows.count()).isEqualTo(paymentsBefore);
    assertThat(auditRows.count()).isEqualTo(auditBefore);
    verify(gateway, never()).transferToken(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
  }

  @Test
  void the_policy_verdict_is_passed_through_as_returned() {
    policyAnswers(Verdict.HOLD);

    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("NEEDS_APPROVAL");
    assertThat(p.policy().verdict()).isEqualTo("HOLD");
    assertThat(p.policy().ruleId()).isEqualTo("rule.HOLD");
    assertThat(p.policy().reason()).isEqualTo("because HOLD");
  }

  @Test
  void a_denied_payment_is_blocked_before_any_transaction() {
    policyAnswers(Verdict.DENY);

    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("BLOCKED");
    assertThat(p.summary()).contains("no Hedera transaction would be created");
  }

  @Test
  void a_non_associated_recipient_without_automatic_slots_would_fail() {
    when(mirror.findAssociation(RECIPIENT, TOKEN)).thenReturn(LookupState.NOT_FOUND);

    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("LIKELY_TO_FAIL");
    assertThat(p.checks())
        .filteredOn(c -> c.status().equals("FAIL"))
        .singleElement()
        .satisfies(c -> assertThat(c.detail()).contains("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT"));
  }

  @Test
  void a_recipient_with_unlimited_automatic_associations_can_receive_the_token() {
    when(mirror.findAssociation(RECIPIENT, TOKEN)).thenReturn(LookupState.NOT_FOUND);
    when(mirror.findAccount(RECIPIENT))
        .thenReturn(new AccountLookup(LookupState.FOUND, new AccountInfo(false, -1)));

    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("READY");
    assertThat(p.checks())
        .filteredOn(c -> c.name().startsWith("Recipient can receive"))
        .singleElement()
        .satisfies(c -> assertThat(c.detail()).contains("automatic associations"));
  }

  @Test
  void more_than_the_balance_would_fail_and_shows_no_negative_balance() {
    PaymentPreview p = previews.preview(paytest(150));

    assertThat(p.outcome()).isEqualTo("LIKELY_TO_FAIL");
    assertThat(p.balanceBefore()).isEqualTo("100");
    assertThat(p.balanceAfter()).isNull();
  }

  @Test
  void simulation_mode_checks_nothing_on_the_ledger() {
    when(gateway.payerAccount()).thenReturn(null);

    PaymentPreview p = previews.preview(paytest(25));

    assertThat(p.outcome()).isEqualTo("SIMULATION");
    assertThat(p.checks()).extracting("status").containsOnly("UNKNOWN");
  }

  @Test
  void executing_asks_the_policy_again_and_follows_its_new_answer() {
    assertThat(previews.preview(paytest(25)).outcome()).isEqualTo("READY");

    // The policy changed its mind between preview and execute (a limit was lowered, say).
    policyAnswers(Verdict.DENY);
    when(gateway.newTransactionId()).thenReturn(PAYER + "@1790195836.000000001");
    when(gateway.transferToken(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(new PaymentResult(Outcome.SUCCESS, null, "SUCCESS", PAYER, false));

    assertThat(payments.create(paytest(25)).status()).isEqualTo("REJECTED");
    verify(gateway, never()).transferToken(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
  }
}
