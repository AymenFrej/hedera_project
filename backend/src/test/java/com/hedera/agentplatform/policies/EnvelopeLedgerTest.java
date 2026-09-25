package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A budget that never goes down is not a budget. These tests pin when money actually leaves an
 * envelope: on ALLOW immediately, on HOLD only once a human says yes, and never on DENY or on a
 * refusal.
 */
@SpringBootTest
@Transactional
class EnvelopeLedgerTest {

  @Autowired private ApprovalService approvals;
  @Autowired private EnvelopeLedger ledger;

  @Test
  void an_allowed_spend_leaves_the_envelope_smaller() {
    long before = ledger.state().balances().get(Envelope.RENT);

    approvals.submit(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), ledger.state());

    assertThat(ledger.state().balances().get(Envelope.RENT)).isEqualTo(before - 100);
  }

  @Test
  void a_held_spend_holds_the_money_too_until_a_human_answers() {
    long before = ledger.state().balances().get(Envelope.EMERGENCY);

    approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), ledger.state());

    assertThat(ledger.state().balances().get(Envelope.EMERGENCY))
        .as("a pending question must not move money")
        .isEqualTo(before);
  }

  @Test
  void approving_a_held_spend_debits_the_envelope() {
    long before = ledger.state().balances().get(Envelope.EMERGENCY);
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), ledger.state());

    approvals.approve(submission.approval().id());

    assertThat(ledger.state().balances().get(Envelope.EMERGENCY)).isEqualTo(before - 50);
  }

  @Test
  void rejecting_a_held_spend_leaves_the_envelope_untouched() {
    long before = ledger.state().balances().get(Envelope.EMERGENCY);
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), ledger.state());

    approvals.reject(submission.approval().id());

    assertThat(ledger.state().balances().get(Envelope.EMERGENCY)).isEqualTo(before);
  }

  @Test
  void a_denied_spend_debits_nothing() {
    long before = ledger.state().balances().get(Envelope.RENT);

    approvals.submit(new PolicyRequest(Envelope.RENT, before + 1, "landlord-tunis"), ledger.state());

    assertThat(ledger.state().balances().get(Envelope.RENT)).isEqualTo(before);
  }

  @Test
  void the_next_decision_is_judged_against_what_is_left_not_the_original_budget() {
    long rent = ledger.state().balances().get(Envelope.RENT);
    approvals.submit(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), ledger.state());

    ApprovalService.Submission second =
        approvals.submit(new PolicyRequest(Envelope.RENT, rent - 50, "landlord-tunis"), ledger.state());

    assertThat(second.decision().verdict())
        .as("affordable against the opening runway, unaffordable against what is left")
        .isEqualTo(Verdict.DENY);
    assertThat(second.decision().ruleId()).isEqualTo("funds.insufficient");
  }
}
