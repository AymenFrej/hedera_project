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
 * A verdict and the budget it was computed against must arrive together. When the console has to
 * fetch the balances separately it shows 500 rent right next to a verdict that just spent 400 of
 * it: two numbers on one screen, one of them false.
 */
@SpringBootTest
@Transactional
class DecideReturnsEnvelopesTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mvc() {
    return com.hedera.agentplatform.accounts.AuthenticatedMvc.admin(context);
  }

  @Test
  void an_allowed_spend_answers_with_the_envelope_already_debited() throws Exception {
    mvc()
        .perform(
            post("/api/v1/policies/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"envelope\":\"RENT\",\"amount\":100,\"counterparty\":\"landlord-tunis\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("ALLOW"))
        .andExpect(jsonPath("$.envelopes.RENT").value(400))
        .andExpect(jsonPath("$.envelopes.ESSENTIALS").value(300));
  }

  @Test
  void a_held_spend_answers_with_the_money_still_in_the_envelope() throws Exception {
    mvc()
        .perform(
            post("/api/v1/policies/decide")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"envelope\":\"EMERGENCY\",\"amount\":50,\"counterparty\":\"landlord-tunis\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verdict").value("HOLD"))
        .andExpect(jsonPath("$.envelopes.EMERGENCY").value(200));
  }
}
