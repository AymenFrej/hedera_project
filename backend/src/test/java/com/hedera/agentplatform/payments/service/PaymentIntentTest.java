package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.payments.agent.PaymentAgent;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.Outcome;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** From "Pay Zied 5 PAYTEST, keep at least 10" to a payment: resolution, condition, agent. */
@SpringBootTest
@Transactional
class PaymentIntentTest {

  private static final String PAYER = "0.0.5239440";
  private static final String ZIED = "0.0.10682427";
  private static final String PAYTEST = "0.0.10687138";

  @Autowired private IntentService intents;
  @Autowired private ContactService contacts;
  @Autowired private PaymentService payments;
  @Autowired private PaymentAgent agent;
  @Autowired private AuditEventRepository auditRows;
  @MockitoBean private HederaPaymentGateway gateway;
  @MockitoBean private PaymentMirrorClient mirror;
  @MockitoBean private TokenDecimals decimals;
  @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private PaymentPolicy policy;

  @BeforeEach
  void liveAccountHolding100Paytest() {
    when(gateway.payerAccount()).thenReturn(PAYER);
    when(gateway.isLive()).thenReturn(true);
    when(gateway.newTransactionId()).thenReturn(PAYER + "@1790195836.000000001");
    when(gateway.transferToken(anyString(), anyString(), anyString(), anyLong(), any()))
        .thenReturn(new PaymentResult(Outcome.SUCCESS, PAYER + "@1790195836.000000001", "SUCCESS", PAYER, false));
    when(mirror.findBalances(PAYER))
        .thenReturn(
            new BalanceLookup(
                LookupState.FOUND,
                new AccountBalances(PAYER, 1_000_000_000L, "1790195836.839746104",
                    List.of(new TokenHolding(PAYTEST, "PAYTEST", "Demo", 0, 100L)))));
    when(decimals.of(null)).thenReturn(TokenDecimals.HBAR);
    when(decimals.of(anyString())).thenReturn(0);
    when(policy.evaluate(any())).thenReturn(new PaymentPolicyDecision(Verdict.ALLOW, "policy.ok", "ok"));
    contacts.add("Zied", ZIED);
  }

  private static PaymentIntent intent(String recipient, String amount, String asset, String keep) {
    return new PaymentIntent(recipient, amount, asset, "essentials", null, keep);
  }

  @Test
  void a_contact_name_and_a_token_symbol_resolve_from_real_sources() {
    IntentUnderstanding u = intents.understand(intent("zied", "5", "paytest", "10"));

    assertThat(u.understood()).isTrue();
    assertThat(u.request().destination()).isEqualTo(ZIED);
    assertThat(u.request().tokenId()).isEqualTo(PAYTEST);
    assertThat(u.request().envelope()).isEqualTo("ESSENTIALS");
    assertThat(u.request().keepAtLeast()).isEqualTo("10");
    assertThat(u.recipientName()).isEqualTo("Zied");
    assertThat(u.steps())
        .extracting(IntentUnderstanding.Resolution::source)
        .contains("your contacts", "tokens held by the paying account (Mirror Node)");
  }

  @Test
  void nothing_is_guessed() {
    IntentUnderstanding u = intents.understand(intent("Ziad", "5", "USDC", null));

    assertThat(u.understood()).isFalse();
    assertThat(u.request()).isNull();
    assertThat(u.problems())
        .contains(
            "\"Ziad\" is not one of your contacts",
            "The paying account holds no token with symbol USDC");
  }

  @Test
  void no_envelope_is_asked_for_instead_of_sent_to_a_certain_policy_denial() {
    IntentUnderstanding u =
        intents.understand(new PaymentIntent("0.0.4242", "0.05", null, null, null, null));

    assertThat(u.understood()).as("the policy denies a request without an envelope").isFalse();
    assertThat(u.request()).isNull();
    assertThat(u.envelopeChoices())
        .as("the choices are the policy's own envelopes")
        .containsExactlyElementsOf(
            java.util.Arrays.stream(com.hedera.agentplatform.policies.PolicyEngine.Envelope.values())
                .map(e -> e.name().toLowerCase(java.util.Locale.ROOT))
                .toList());
    assertThat(u.problems()).anyMatch(p -> p.startsWith("Choose which envelope pays"));
  }

  @Test
  void an_envelope_that_does_not_exist_is_not_guessed() {
    IntentUnderstanding u =
        intents.understand(new PaymentIntent("0.0.4242", "1", null, "holidays", null, null));

    assertThat(u.understood()).isFalse();
    assertThat(u.problems()).anyMatch(p -> p.contains("no envelope called \"holidays\""));
    assertThat(u.envelopeChoices()).isNotEmpty();
  }

  @Test
  void an_account_id_and_hbar_need_no_lookup() {
    IntentUnderstanding u = intents.understand(intent("0.0.4242", "1.5", null, null));

    assertThat(u.understood()).isTrue();
    assertThat(u.request().destination()).isEqualTo("0.0.4242");
    assertThat(u.request().tokenId()).isNull();
    assertThat(u.assetSymbol()).isEqualTo("HBAR");
  }

  @Test
  void a_condition_that_would_not_hold_stops_the_payment_before_the_policy_is_asked() {
    // 100 PAYTEST held, 95 sent, keep at least 10: only 5 would remain.
    PaymentResponse p =
        payments.create(new CreatePaymentRequest(ZIED, "95", PAYTEST, "ESSENTIALS", null, "10"));

    assertThat(p.status()).isEqualTo("REJECTED");
    assertThat(p.failureReason()).contains("keep at least 10").contains("only 5 would remain");
    assertThat(p.transactionId()).isNull();
    assertThat(p.policyVerdict()).as("the policy was never asked").isNull();
    verify(policy, never()).submit(any());
    assertThat(auditRows.findAll())
        .anyMatch(e -> "PAYMENT_CONDITION".equals(e.action) && "FAILED".equals(e.status));
  }

  @Test
  void a_condition_that_holds_lets_the_payment_through() {
    PaymentResponse p =
        payments.create(new CreatePaymentRequest(ZIED, "5", PAYTEST, "ESSENTIALS", null, "10"));

    assertThat(p.status()).isEqualTo("CONFIRMED");
    assertThat(p.keepAtLeast()).isEqualTo("10");
  }

  @Test
  void contacts_are_unique_by_name_and_hold_a_real_account_id() {
    assertThatThrownBy(() -> contacts.add("ZIED", "0.0.1")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> contacts.add("Alice", "alice")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_agent_resolves_an_intent_and_shows_each_resolution_in_its_plan() {
    AgentPlan plan =
        agent.plan(new AgentRequest("req_intent_1", AgentIntent.SEND_PAYMENT, "pay zied",
            Map.of("recipient", "Zied", "amount", "5", "asset", "PAYTEST", "envelope", "essentials")));

    assertThat(plan.status()).isEqualTo(AgentStatus.READY);
    assertThat(plan.actions()).extracting("type").startsWith("RESOLVE", "RESOLVE", "RESOLVE");
    assertThat(plan.actions()).extracting("description").anyMatch(d -> d.toString().contains(ZIED));

    AgentPlan unknown =
        agent.plan(new AgentRequest("req_intent_2", AgentIntent.SEND_PAYMENT, "pay bob",
            Map.of("recipient", "Bob", "amount", "5")));
    assertThat(unknown.status()).isEqualTo(AgentStatus.FAILED);
  }
}
