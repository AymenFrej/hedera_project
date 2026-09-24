package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Rules of the policy engine. These are the claims a judge can replay offline: no network, no SDK,
 * no model. A decision is a value computed from (request, budget, rules).
 */
class PolicyEngineTest {

  @Test
  void allocateSplitsExactlyWithNoFundsCreatedOrLost() {
    for (long total : new long[] {1000, 999, 1, 7, 123457}) {
      Map<Envelope, Long> envelopes = PolicyEngine.allocate(total);
      long sum = envelopes.values().stream().mapToLong(Long::longValue).sum();
      assertThat(sum).as("envelopes must sum to %d", total).isEqualTo(total);
    }
  }

  @Test
  void allocateRejectsNonPositiveTotals() {
    for (long bad : new long[] {0, -5}) {
      assertThatThrownBy(() -> PolicyEngine.allocate(bad))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static final List<String> KNOWN = List.of("0.0.5551212", "landlord-tunis");

  private static PolicyState state(Map<Envelope, Long> balances) {
    return new PolicyState(balances, KNOWN);
  }

  @Test
  void denyBeatsHoldSoAnUnsettleableTransferIsNeverOfferedToAHuman() {
    // Unknown counterparty AND over balance. Must DENY, never HOLD: a human
    // must not be able to approve a transfer that cannot settle.
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, 9999, "stranger"), state(Map.of(Envelope.RENT, 500L)));

    assertThat(d.verdict()).isEqualTo(Verdict.DENY);
    assertThat(d.ruleId()).isEqualTo("funds.insufficient");
  }

  @Test
  void deniesASpendWithNoPayeeInsteadOfAskingAHumanToApproveIt() {
    // A transfer with no counterparty cannot settle: there is nobody to pay. Holding it puts
    // "first transfer to null" in the approvals queue, where a human can approve a payment that
    // will never execute. DENY beats HOLD.
    for (String noPayee : new String[] {null, "", "   "}) {
      PolicyDecision d =
          PolicyEngine.decide(
              new PolicyRequest(Envelope.RENT, 10, noPayee), state(Map.of(Envelope.RENT, 500L)));

      assertThat(d.verdict()).as("payee %s must be denied", noPayee).isEqualTo(Verdict.DENY);
      assertThat(d.ruleId()).isEqualTo("counterparty.missing");
    }
  }

  @Test
  void allowsAModestAmountToAKnownCounterparty() {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"),
            state(Map.of(Envelope.RENT, 500L, Envelope.ESSENTIALS, 300L)));

    assertThat(d.verdict()).isEqualTo(Verdict.ALLOW);
    assertThat(d.balanceAfter()).isEqualTo(400L);
  }

  @Test
  void holdsEveryEmergencySpendEvenOneUnitToAKnownCounterparty() {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.EMERGENCY, 1, "landlord-tunis"),
            state(Map.of(Envelope.EMERGENCY, 200L)));

    assertThat(d.verdict()).isEqualTo(Verdict.HOLD);
    assertThat(d.ruleId()).isEqualTo("emergency.human");
  }

  @Test
  void holdsTheFirstTransferToAnUnknownCounterparty() {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.ESSENTIALS, 10, "new-shop"),
            state(Map.of(Envelope.ESSENTIALS, 300L)));

    assertThat(d.verdict()).isEqualTo(Verdict.HOLD);
    assertThat(d.ruleId()).isEqualTo("counterparty.unknown");
  }

  @Test
  void holdsALargeSingleMoveAgainstTheRemainingEnvelope() {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, 300, "landlord-tunis"),
            state(Map.of(Envelope.RENT, 500L)));

    assertThat(d.verdict()).isEqualTo(Verdict.HOLD);
    assertThat(d.ruleId()).isEqualTo("amount.large");
  }

  @Test
  void deniesAnUnfundedEnvelope() {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, 1, "landlord-tunis"),
            state(Map.of(Envelope.ESSENTIALS, 1L)));

    assertThat(d.verdict()).isEqualTo(Verdict.DENY);
    assertThat(d.ruleId()).isEqualTo("envelope.unfunded");
  }

  @Test
  void deniesNonPositiveAmounts() {
    for (long bad : new long[] {0, -1}) {
      PolicyDecision d =
          PolicyEngine.decide(
              new PolicyRequest(Envelope.RENT, bad, "landlord-tunis"),
              state(Map.of(Envelope.RENT, 500L)));

      assertThat(d.verdict()).as("amount %d must be denied", bad).isEqualTo(Verdict.DENY);
      assertThat(d.ruleId()).isEqualTo("amount.invalid");
    }
  }

  @Test
  void decideNeverMutatesTheStateItIsGiven() {
    Map<Envelope, Long> balances = new EnumMap<>(Envelope.class);
    balances.put(Envelope.RENT, 500L);
    balances.put(Envelope.EMERGENCY, 200L);
    PolicyState before = new PolicyState(balances, KNOWN);

    PolicyEngine.decide(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), before);

    assertThat(before.balances())
        .containsExactlyInAnyOrderEntriesOf(Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 200L));
    assertThat(before.knownCounterparties()).isEqualTo(KNOWN);
  }

  @Test
  void everyDecisionCarriesARuleIdAndAHumanReason() {
    List<PolicyRequest> cases =
        List.of(
            new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"),
            new PolicyRequest(Envelope.EMERGENCY, 5, "landlord-tunis"),
            new PolicyRequest(Envelope.ESSENTIALS, 5, "stranger"));

    for (PolicyRequest c : cases) {
      PolicyDecision d =
          PolicyEngine.decide(c, state(Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 200L)));
      assertThat(d.ruleId()).as("rule id present").isNotBlank();
      assertThat(d.reason()).as("reason present").isNotBlank();
      assertThat(d.verdict()).isNotNull();
    }
  }
}
