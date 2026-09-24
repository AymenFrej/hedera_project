package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * A verdict a judge cannot open is a claim. The decision response has to hand back the id of the
 * audit event it wrote, so the proof is one click away instead of a promise.
 */
@SpringBootTest
@Transactional
class DecisionProofLinkTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mvc() {
    return com.hedera.agentplatform.accounts.AuthenticatedMvc.admin(context);
  }

  private static final String RENT_SPEND =
      "{\"envelope\":\"RENT\",\"amount\":100,\"counterparty\":\"landlord-tunis\"}";

  @Test
  void a_verdict_carries_the_id_of_the_audit_event_it_wrote() throws Exception {
    String body =
        mvc()
            .perform(post("/api/v1/policies/decide").contentType(MediaType.APPLICATION_JSON).content(RENT_SPEND))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auditEventId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode decision = new ObjectMapper().readTree(body);

    mvc()
        .perform(get("/api/v1/audit/" + decision.get("auditEventId").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("POLICY_DECISION"))
        .andExpect(jsonPath("$.status").value("ALLOW"));
  }

  @Test
  void a_decision_that_never_reached_the_ledger_says_so_rather_than_implying_a_proof()
      throws Exception {
    mvc()
        .perform(post("/api/v1/policies/decide").contentType(MediaType.APPLICATION_JSON).content(RENT_SPEND))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anchored").value(false));
  }
}
