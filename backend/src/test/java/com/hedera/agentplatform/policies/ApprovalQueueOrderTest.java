package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.dto.ApprovalResponse;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.PolicyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** The queue a human reads: the request that just arrived must be the one on top. */
@SpringBootTest
@Transactional
class ApprovalQueueOrderTest {

  @Autowired private ApprovalService approvals;
  @Autowired private PolicyService policies;

  private static final PolicyState STATE =
      new PolicyState(
          Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 500L), List.of("landlord-tunis"));

  @Test
  void the_most_recent_request_is_listed_first() {
    String older =
        approvals
            .submit(new PolicyRequest(Envelope.EMERGENCY, 10, "landlord-tunis"), STATE)
            .approval()
            .id();
    String newer =
        approvals
            .submit(new PolicyRequest(Envelope.EMERGENCY, 20, "landlord-tunis"), STATE)
            .approval()
            .id();

    List<String> ids = policies.approvals().stream().map(ApprovalResponse::id).toList();

    assertThat(ids.indexOf(newer)).isLessThan(ids.indexOf(older));
  }
}
