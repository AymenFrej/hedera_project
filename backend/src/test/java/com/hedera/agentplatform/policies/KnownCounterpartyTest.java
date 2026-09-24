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
 * "First transfer to an unknown counterparty needs a human" only makes sense if answering it once
 * settles the question. Otherwise the rule quietly becomes "every transfer needs a human".
 */
@SpringBootTest
@Transactional
class KnownCounterpartyTest {

  @Autowired private ApprovalService approvals;
  @Autowired private EnvelopeLedger ledger;

  @Test
  void approving_a_first_transfer_makes_the_counterparty_known() {
    ApprovalService.Submission first =
        approvals.submit(new PolicyRequest(Envelope.ESSENTIALS, 20, "new-shop"), ledger.state());
    assertThat(first.decision().ruleId()).isEqualTo("counterparty.unknown");

    approvals.approve(first.approval().id());

    ApprovalService.Submission second =
        approvals.submit(new PolicyRequest(Envelope.ESSENTIALS, 20, "new-shop"), ledger.state());
    assertThat(second.decision().verdict()).isEqualTo(Verdict.ALLOW);
    assertThat(second.decision().ruleId()).isEqualTo("policy.ok");
  }

  @Test
  void rejecting_a_first_transfer_leaves_the_counterparty_unknown() {
    ApprovalService.Submission first =
        approvals.submit(new PolicyRequest(Envelope.ESSENTIALS, 20, "dodgy-shop"), ledger.state());

    approvals.reject(first.approval().id());

    ApprovalService.Submission second =
        approvals.submit(new PolicyRequest(Envelope.ESSENTIALS, 20, "dodgy-shop"), ledger.state());
    assertThat(second.decision().verdict()).isEqualTo(Verdict.HOLD);
    assertThat(second.decision().ruleId()).isEqualTo("counterparty.unknown");
  }
}
