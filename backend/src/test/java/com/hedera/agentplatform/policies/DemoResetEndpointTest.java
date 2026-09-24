package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The button that makes the demo runnable twice. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoResetEndpointTest {

  @Autowired private org.springframework.web.context.WebApplicationContext context;
  private MockMvc mockMvc;

  @org.junit.jupiter.api.BeforeEach
  void authenticate() { mockMvc = com.hedera.agentplatform.accounts.AuthenticatedMvc.admin(context); }

  @Test
  void resetting_hands_back_the_opening_balances() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/policies/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"envelope\":\"RENT\",\"amount\":100,\"counterparty\":\"landlord-tunis\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/v1/policies/demo/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envelopes.RENT").value(500))
        .andExpect(jsonPath("$.envelopes.ESSENTIALS").value(300))
        .andExpect(jsonPath("$.envelopes.EMERGENCY").value(200));

    mockMvc
        .perform(get("/api/v1/policies/approvals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
