package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
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
}
