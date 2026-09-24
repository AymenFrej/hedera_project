package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.dto.ApprovalResponse;
import com.hedera.agentplatform.policies.service.ApprovalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** The approve/reject routes named in the issue, exercised over HTTP. */
@SpringBootTest
@Transactional
class ApprovalControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ApprovalService approvals;
  @Autowired private ObjectMapper objectMapper;

  private String pendingApprovalId() {
    PolicyState state =
        new PolicyState(
            Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 200L), List.of("landlord-tunis"));
    ApprovalResponse approval =
        approvals.submit(new PolicyRequest(Envelope.EMERGENCY, 50, "landlord-tunis"), state)
            .approval();
    return approval.id();
  }

  @Test
  void posting_approve_settles_the_request() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    mockMvc
        .perform(post("/api/v1/policies/approvals/{id}/approve", pendingApprovalId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.decidedBy").value("platform"));
  }

  @Test
  void answering_the_same_request_twice_is_a_409_not_a_500() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    String id = pendingApprovalId();

    mockMvc.perform(post("/api/v1/policies/approvals/{id}/approve", id)).andExpect(status().isOk());

    mockMvc
        .perform(post("/api/v1/policies/approvals/{id}/reject", id))
        .andExpect(status().isConflict());
  }

  @Test
  void answering_an_unknown_approval_is_a_404_not_a_500() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    mockMvc
        .perform(post("/api/v1/policies/approvals/{id}/reject", "approval_does_not_exist"))
        .andExpect(status().isNotFound());
  }
}
