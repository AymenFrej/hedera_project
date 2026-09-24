package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A demo you can only run once is not a demo. After a walkthrough the envelopes are spent and the
 * queue is full of old questions, so the second run in front of a jury shows neither the budget nor
 * a readable queue.
 */
@SpringBootTest
@Transactional
class DemoResetTest {

  @Autowired private ApprovalService approvals;
  @Autowired private EnvelopeLedger ledger;
  @Autowired private AuditEventRepository auditEvents;

  @Test
  void reset_puts_the_runway_back_to_its_seeded_amounts() {
    approvals.submit(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), ledger.state());

    ledger.reset();

    assertThat(ledger.state().balances())
        .containsEntry(Envelope.RENT, 500L * 100_000_000L)
        .containsEntry(Envelope.ESSENTIALS, 300L * 100_000_000L)
        .containsEntry(Envelope.EMERGENCY, 200L * 100_000_000L);
  }

  @Test
  void reset_empties_the_queue_of_questions_waiting_for_a_human() {
    approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), ledger.state());

    ledger.reset();

    assertThat(approvals.pending()).isEmpty();
  }

  @Test
  void reset_forgets_counterparties_a_human_vouched_for() {
    ApprovalService.Submission held =
        approvals.submit(new PolicyRequest(Envelope.RENT, 20, "brand-new-guy"), ledger.state());
    approvals.approve(held.approval().id());
    assertThat(ledger.state().knownCounterparties()).contains("brand-new-guy");

    ledger.reset();

    assertThat(ledger.state().knownCounterparties()).containsExactly("landlord-tunis");
  }

  @Test
  void reset_never_erases_the_audit_trail() {
    approvals.submit(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), ledger.state());
    long recorded = auditEvents.count();
    assertThat(recorded).isPositive();

    ledger.reset();

    assertThat(auditEvents.count())
        .as("an audit trail you can wipe proves nothing")
        .isGreaterThanOrEqualTo(recorded);
  }
}
