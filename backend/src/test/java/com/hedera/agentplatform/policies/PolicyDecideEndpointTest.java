package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The route a judge actually exercises: ask the platform to spend, get a verdict back. Without it
 * the rule engine is unreachable from outside the JVM.
 */
@SpringBootTest
@Transactional
class PolicyDecideEndpointTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mvc() {
    return MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void a_routine_spend_to_a_known_counterparty_is_allowed() throws Exception {
    mvc()
        .perform(
            post("/api/v1/policies/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"envelope\":\"RENT\",\"amount\":100,\"counterparty\":\"landlord-tunis\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("ALLOW"))
        .andExpect(jsonPath("$.ruleId").value("policy.ok"))
        .andExpect(jsonPath("$.approvalId").doesNotExist());
  }

  @Test
  void an_emergency_spend_is_held_and_returns_an_approval_handle() throws Exception {
    mvc()
        .perform(
            post("/api/v1/policies/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"envelope\":\"EMERGENCY\",\"amount\":50,\"counterparty\":\"landlord-tunis\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("HOLD"))
        .andExpect(jsonPath("$.ruleId").value("emergency.human"))
        .andExpect(jsonPath("$.approvalId").isNotEmpty());
  }
}
