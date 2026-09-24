package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envelopes are denominated in tinybars, the same unit Payments sends.
 *
 * <p>This test exists because they were not. The runway was seeded as 500/300/200 with no unit
 * while Payments passes Hedera's native tinybars, so a real 2 HBAR rent payment arrived as
 * 200000000 against a 500 balance and came back DENY funds.insufficient. Every honest payment was
 * refused, and the demo only worked with amounts too small to be money.
 */
@SpringBootTest
@Transactional
class EnvelopeUnitTest {

  private static final long TINYBARS_PER_HBAR = 100_000_000L;

  @Autowired private EnvelopeLedger ledger;

  @Test
  void a_real_rent_payment_in_tinybars_is_affordable() {
    long twoHbar = 2 * TINYBARS_PER_HBAR;

    PolicyDecision decision =
        PolicyEngine.decide(
            new PolicyRequest(Envelope.RENT, twoHbar, "landlord-tunis"), ledger.state());

    assertThat(decision.verdict()).isNotEqualTo(Verdict.DENY);
    assertThat(decision.ruleId()).isNotEqualTo("funds.insufficient");
  }

  @Test
  void the_rent_envelope_holds_five_hundred_hbar_expressed_in_tinybars() {
    assertThat(ledger.state().balances().get(Envelope.RENT))
        .isEqualTo(500 * TINYBARS_PER_HBAR);
  }
}
