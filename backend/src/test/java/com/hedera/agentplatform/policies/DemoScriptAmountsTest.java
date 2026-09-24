package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The terminal half of the walkthrough posts amounts straight to /decide, so those amounts are in
 * the unit the engine decides in — tinybars — not in HBAR.
 *
 * <p>This test exists because they were not. The screens send what a person typed through a ℏ →
 * tinybar conversion, so the click-through run kept working; the copy-pasted curl commands did not.
 * "amount":9999 is 0.0001 ℏ against a 500 ℏ envelope, so the step the document presents as its
 * centerpiece — DENIED, funds.insufficient, nobody is asked — actually came back ALLOW.
 *
 * <p>It asserts the verdicts the prose claims, not the sentences around them, so rewording the
 * walkthrough does not read as a regression.
 */
@SpringBootTest
@Transactional
class DemoScriptAmountsTest {

  private static final Path DEMO = Path.of("..", "demo", "policies.md");

  /** A documented curl body: envelope, amount and counterparty as the reader would send them. */
  private static final Pattern DECIDE_BODY =
      Pattern.compile(
          "\\{\"envelope\":\"(\\w+)\",\"amount\":(\\d+),\"counterparty\":\"([^\"]+)\"\\}");

  @Autowired private EnvelopeLedger ledger;

  private List<PolicyRequest> documentedRequests() throws IOException {
    Matcher matcher = DECIDE_BODY.matcher(Files.readString(DEMO));
    List<PolicyRequest> requests = new ArrayList<>();
    while (matcher.find()) {
      requests.add(
          new PolicyRequest(
              Envelope.valueOf(matcher.group(1)),
              Long.parseLong(matcher.group(2)),
              matcher.group(3)));
    }
    return requests;
  }

  @Test
  void the_documented_terminal_run_produces_the_verdicts_it_claims() throws IOException {
    ledger.reset();
    PolicyState opening = ledger.state();

    List<Verdict> verdicts =
        documentedRequests().stream().map(request -> PolicyEngine.decide(request, opening).verdict())
            .toList();

    assertThat(verdicts)
        .as("verdicts of the three /decide calls documented in demo/policies.md")
        .containsExactly(Verdict.ALLOW, Verdict.DENY, Verdict.HOLD);
  }

  @Test
  void the_amount_the_walkthrough_calls_refused_is_unaffordable() throws IOException {
    ledger.reset();

    PolicyRequest refused = documentedRequests().get(1);
    PolicyDecision decision = PolicyEngine.decide(refused, ledger.state());

    assertThat(decision.ruleId()).isEqualTo("funds.insufficient");
  }
}
