package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envelopes are a budget in HBAR. A token has its own supply and its own decimals, so its units are
 * not comparable to tinybars: 2 BEAN is not 200 tinybars, and subtracting one from the other spends
 * rent money that was never requested.
 *
 * <p>Reported by Maryem from the Payments side. The counterparty rules still apply to a token
 * payment — a first transfer to an unknown account is held for a human whatever the asset — but the
 * envelope must not move.
 */
@SpringBootTest
@Transactional
class TokenPaymentsDoNotSpendEnvelopesTest {

  @Autowired private EnvelopeLedger ledger;

  private static final long TWO_BEAN = 2L;

  @Test
  void a_token_payment_leaves_the_hbar_envelope_untouched() {
    long rentBefore = ledger.state().balances().get(Envelope.RENT);

    PolicyDecision decision =
        PolicyEngine.decide(
            PolicyRequest.ofToken(Envelope.RENT, TWO_BEAN, "landlord-tunis", "BEAN"),
            ledger.state());

    assertThat(decision.balanceAfter())
        .as("no HBAR figure is quoted for a token spend: it would explain nothing")
        .isNull();
    assertThat(ledger.state().balances().get(Envelope.RENT))
        .as("a token spend does not move an HBAR envelope")
        .isEqualTo(rentBefore);
  }

  @Test
  void a_token_payment_to_an_unknown_counterparty_is_still_held() {
    PolicyDecision decision =
        PolicyEngine.decide(
            PolicyRequest.ofToken(Envelope.RENT, TWO_BEAN, "someone-new", "BEAN"),
            ledger.state());

    assertThat(decision.verdict()).isEqualTo(Verdict.HOLD);
    assertThat(decision.ruleId()).isEqualTo("counterparty.unknown");
  }

  @Test
  void a_token_payment_is_never_refused_for_lacking_hbar() {
    long rent = ledger.state().balances().get(Envelope.RENT);

    PolicyDecision decision =
        PolicyEngine.decide(
            PolicyRequest.ofToken(Envelope.RENT, rent + 1_000_000, "landlord-tunis", "BEAN"),
            ledger.state());

    assertThat(decision.ruleId())
        .as("an HBAR balance says nothing about how much BEAN exists")
        .isNotEqualTo("funds.insufficient");
  }

  @Test
  void an_hbar_payment_still_spends_its_envelope() {
    long rentBefore = ledger.state().balances().get(Envelope.RENT);
    long oneHbar = 100_000_000L;

    PolicyDecision decision =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, oneHbar, "landlord-tunis"), ledger.state());

    assertThat(decision.balanceAfter()).isEqualTo(rentBefore - oneHbar);
  }
}
