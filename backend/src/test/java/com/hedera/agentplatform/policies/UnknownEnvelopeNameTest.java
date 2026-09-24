package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A refusal has to be true. Naming an envelope that does not exist is a different mistake from
 * naming none at all, and the sentence on screen is what a judge reads.
 */
class UnknownEnvelopeNameTest {

  private static final PolicyState STATE =
      new PolicyState(
          Map.of(Envelope.RENT, 500L, Envelope.ESSENTIALS, 300L, Envelope.EMERGENCY, 200L),
          List.of("landlord-tunis"));

  @Test
  void a_refusal_names_the_envelope_that_does_not_exist() {
    PolicyDecision decision =
        PolicyEngine.decide(new PolicyRequest(null, "YACHT", 10, "landlord-tunis"), STATE);

    assertThat(decision.verdict()).isEqualTo(Verdict.DENY);
    assertThat(decision.ruleId()).isEqualTo("envelope.unknown");
    assertThat(decision.reason()).contains("YACHT");
  }

  @Test
  void a_request_naming_no_envelope_at_all_still_says_so() {
    PolicyDecision decision =
        PolicyEngine.decide(new PolicyRequest(null, null, 10, "landlord-tunis"), STATE);

    assertThat(decision.verdict()).isEqualTo(Verdict.DENY);
    assertThat(decision.ruleId()).isEqualTo("envelope.unknown");
    assertThat(decision.reason()).isEqualTo("no envelope named in the request");
  }
}
