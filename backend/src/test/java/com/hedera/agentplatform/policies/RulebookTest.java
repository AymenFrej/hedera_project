package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The published rulebook has to be the rules the engine actually applies. A catalogue written by
 * hand drifts the first time a rule is added; this test drives real requests through the engine and
 * fails when a rule id it emitted is missing from the list.
 */
class RulebookTest {

  private static final List<String> KNOWN = List.of("landlord-tunis");

  private static PolicyState state(Map<Envelope, Long> balances) {
    return new PolicyState(balances, KNOWN);
  }

  /** One request per rule the engine can reach. */
  private static final List<PolicyRequest> EVERY_RULE =
      List.of(
          new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), // policy.ok
          new PolicyRequest(Envelope.EMERGENCY, 10, "landlord-tunis"), // emergency.human
          new PolicyRequest(Envelope.ESSENTIALS, 10, "new-shop"), // counterparty.unknown
          new PolicyRequest(Envelope.RENT, 300, "landlord-tunis"), // amount.large
          new PolicyRequest(Envelope.RENT, 10, ""), // counterparty.missing
          new PolicyRequest(Envelope.RENT, 0, "landlord-tunis"), // amount.invalid
          new PolicyRequest(Envelope.RENT, 9999, "landlord-tunis"), // funds.insufficient
          new PolicyRequest(null, 10, "landlord-tunis")); // envelope.unknown

  @Test
  void the_rulebook_names_every_rule_the_engine_can_emit() {
    PolicyState state =
        state(Map.of(Envelope.RENT, 500L, Envelope.ESSENTIALS, 300L, Envelope.EMERGENCY, 200L));

    Set<String> emitted = new LinkedHashSet<>();
    for (PolicyRequest request : EVERY_RULE) {
      emitted.add(PolicyEngine.decide(request, state).ruleId());
    }
    // envelope.unfunded needs a budget where the envelope is absent entirely.
    emitted.add(
        PolicyEngine.decide(
                new PolicyRequest(Envelope.RENT, 1, "landlord-tunis"),
                state(Map.of(Envelope.ESSENTIALS, 1L)))
            .ruleId());

    Set<String> published =
        Rulebook.rules().stream().map(Rulebook.Rule::ruleId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    assertThat(published).as("published rulebook must cover every emitted rule").containsAll(emitted);
  }

  @Test
  void every_published_rule_carries_a_verdict_and_a_readable_sentence() {
    assertThat(Rulebook.rules()).isNotEmpty();
    for (Rulebook.Rule rule : Rulebook.rules()) {
      assertThat(rule.ruleId()).isNotBlank();
      assertThat(rule.reason()).as("rule %s needs a sentence", rule.ruleId()).isNotBlank();
      assertThat(rule.verdict()).as("rule %s needs a verdict", rule.ruleId()).isNotNull();
    }
  }
}
