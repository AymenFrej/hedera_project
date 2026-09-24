package com.hedera.agentplatform.tokens.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Through the real sessions and the Accounts interceptor, without Hedera credentials (tests run
 * simulated): who may use Tokens, and who sees which operation.
 */
@SpringBootTest
@Transactional
class TokenControllerTest {

  private static final String FIXED =
      "{\"name\":\"Coffee Beans\",\"symbol\":\"BEAN\",\"decimals\":\"0\",\"initialSupply\":\"1000\",\"supplyPolicy\":\"FIXED\"}";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private AuthSessionService sessions;

  private MockMvc as(String userId) {
    String token = sessions.create(users.findById(userId).orElseThrow());
    return MockMvcBuilders.webAppContextSetup(context)
        .defaultRequest(get("/").header("Authorization", "Bearer " + token))
        .build();
  }

  @Test
  void the_studio_previews_promises_before_anything_is_created() throws Exception {
    as("user_demo")
        .perform(post("/api/v1/tokens/preview").contentType(MediaType.APPLICATION_JSON).content(FIXED))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.live").value(false))
        .andExpect(jsonPath("$.promises[1].title").value("Supply fixed forever at 1000 BEAN"));
  }

  @Test
  void without_credentials_a_token_is_simulated_never_presented_as_created() throws Exception {
    as("user_demo")
        .perform(post("/api/v1/tokens").header("Idempotency-Key", "studio-simulated-1")
            .contentType(MediaType.APPLICATION_JSON).content(FIXED))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SIMULATED"))
        .andExpect(jsonPath("$.tokenId").doesNotExist())
        .andExpect(jsonPath("$.transactionUrl").doesNotExist());
  }

  @Test
  void a_design_with_problems_is_refused() throws Exception {
    as("user_demo")
        .perform(post("/api/v1/tokens").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"x\",\"symbol\":\"not a symbol\",\"decimals\":\"0\",\"initialSupply\":\"1\",\"supplyPolicy\":\"FIXED\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void a_user_sees_their_own_operations_and_an_admin_sees_all() throws Exception {
    String body = as("user_demo")
        .perform(post("/api/v1/tokens").header("Idempotency-Key", "studio-mine-1")
            .contentType(MediaType.APPLICATION_JSON).content(FIXED))
        .andReturn().getResponse().getContentAsString();
    String id = new ObjectMapper().readTree(body).path("id").asText();

    as("user_demo").perform(get("/api/v1/tokens/operations/" + id)).andExpect(status().isOk());
    as("admin_demo").perform(get("/api/v1/tokens/operations/" + id)).andExpect(status().isOk());
    as("admin_demo").perform(get("/api/v1/tokens/operations"))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());
  }

  @Test
  void auditors_and_platform_staff_do_not_use_tokens() throws Exception {
    as("auditor_demo").perform(get("/api/v1/tokens")).andExpect(status().isForbidden());
    as("platform_demo").perform(get("/api/v1/tokens")).andExpect(status().isForbidden());
  }

  @Test
  void nobody_signed_out_reaches_tokens() throws Exception {
    MockMvcBuilders.webAppContextSetup(context).build()
        .perform(get("/api/v1/tokens")).andExpect(status().isUnauthorized());
  }
}
