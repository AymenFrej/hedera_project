package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The console has to show the same budget the engine decides against. If the UI carries its own
 * copy of the envelopes, the screen and the verdict drift apart the first time either changes.
 */
@SpringBootTest
@Transactional
class PolicyStateEndpointTest {

  @Autowired private WebApplicationContext context;

  @Test
  void the_state_endpoint_publishes_the_envelopes_the_engine_decides_against() throws Exception {
    MockMvc mockMvc = com.hedera.agentplatform.accounts.AuthenticatedMvc.admin(context);

    mockMvc
        .perform(get("/api/v1/policies/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envelopes.RENT").value(500))
        .andExpect(jsonPath("$.envelopes.ESSENTIALS").value(300))
        .andExpect(jsonPath("$.envelopes.EMERGENCY").value(200))
        .andExpect(jsonPath("$.knownCounterparties[0]").value("landlord-tunis"));
  }
}
