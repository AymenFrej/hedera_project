package com.hedera.agentplatform.policies;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * The lane's documented root endpoint has to show the real rulebook. It used to answer with a
 * single DRAFT row named "Placeholder for spending rules" while the engine was enforcing nine
 * rules: anyone reading the published contract saw a draft instead of the argument of the project.
 */
@SpringBootTest
@Transactional
class RulebookEndpointTest {

  @Autowired private WebApplicationContext context;

  @Test
  void the_policies_endpoint_serves_the_rulebook_not_a_placeholder() throws Exception {
    MockMvc mockMvc = com.hedera.agentplatform.accounts.AuthenticatedMvc.admin(context);

    mockMvc
        .perform(get("/api/v1/policies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(Rulebook.rules().size()))
        .andExpect(jsonPath("$[?(@.ruleId == 'emergency.human')].verdict").value("HOLD"))
        .andExpect(jsonPath("$[?(@.ruleId == 'counterparty.missing')].verdict").value("DENY"))
        .andExpect(jsonPath("$[?(@.ruleId == 'policy.ok')].verdict").value("ALLOW"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("Placeholder"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("DRAFT"))));
  }
}
