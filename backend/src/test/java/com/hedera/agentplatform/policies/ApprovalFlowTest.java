package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.dto.ApprovalResponse;
import com.hedera.agentplatform.policies.service.ApprovalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A HOLD is only half a decision: it is a question put to a human. These tests cover the half that
 * cannot be automated away — who answered, when, and what an answer is not allowed to do.
 */
@SpringBootTest
@Transactional
class ApprovalFlowTest {

  @Autowired private ApprovalService approvals;

  private static PolicyState state() {
    return new PolicyState(
        Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 200L), List.of("landlord-tunis"));
  }

  @Test
  void a_held_decision_creates_a_pending_approval_request() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), state());

    assertThat(submission.decision().verdict()).isEqualTo(Verdict.HOLD);
    assertThat(submission.approval()).isNotNull();
    assertThat(submission.approval().status()).isEqualTo("PENDING");
    assertThat(submission.approval().ruleId()).isEqualTo("emergency.human");
  }

  @Test
  void approving_records_who_answered_and_when() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), state());

    ApprovalResponse approved = approvals.approve(submission.approval().id());

    assertThat(approved.status()).isEqualTo("APPROVED");
    assertThat(approved.decidedBy())
        .as("an approval with no name attached is not an approval")
        .isEqualTo("platform");
    assertThat(approved.decidedAt()).isNotNull();
  }

  @Test
  void rejecting_records_the_refusal_instead_of_silently_dropping_it() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), state());

    ApprovalResponse rejected = approvals.reject(submission.approval().id());

    assertThat(rejected.status()).isEqualTo("REJECTED");
    assertThat(rejected.decidedAt()).isNotNull();
  }

  @Test
  void a_denied_spend_never_becomes_an_approval_request() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.RENT, 9999, "landlord-tunis"), state());

    assertThat(submission.decision().verdict()).isEqualTo(Verdict.DENY);
    assertThat(submission.approval())
        .as("what cannot settle must never be offered to a human to approve")
        .isNull();
  }

  @Test
  void an_allowed_spend_needs_no_human() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), state());

    assertThat(submission.decision().verdict()).isEqualTo(Verdict.ALLOW);
    assertThat(submission.approval()).isNull();
  }

  @Test
  void an_already_answered_request_cannot_be_answered_a_second_time() {
    ApprovalService.Submission submission =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), state());
    String id = submission.approval().id();
    approvals.reject(id);

    assertThatThrownBy(() -> approvals.approve(id))
        .as("a rejection must not be overturned by re-posting the approve route")
        .isInstanceOf(IllegalStateException.class);
  }
}
