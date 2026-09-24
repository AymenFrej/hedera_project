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
 * A HOLD is a question asked at one moment and answered at another. Between the two the money can
 * already be gone: two pending questions can each be affordable on their own and unaffordable
 * together.
 */
@SpringBootTest
@Transactional
class ApprovalAffordabilityTest {

  @Autowired private ApprovalService approvals;
  @Autowired private EnvelopeLedger ledger;

  @Test
  void approving_two_holds_that_only_fit_one_at_a_time_never_overdraws_the_envelope() {
    long emergency = ledger.state().balances().get(Envelope.EMERGENCY);
    long each = emergency - 10;

    ApprovalService.Submission first =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, each, "landlord-tunis"), ledger.state());
    ApprovalService.Submission second =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, each, "landlord-tunis"), ledger.state());

    approvals.approve(first.approval().id());
    try {
      approvals.approve(second.approval().id());
    } catch (IllegalStateException expected) {
      // the second answer must be refused, not silently settled
    }

    assertThat(ledger.state().balances().get(Envelope.EMERGENCY))
        .as("a human clicking approve can never push an envelope below zero")
        .isGreaterThanOrEqualTo(0L);
  }
}
