package com.hedera.agentplatform.payments.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.accounts.TestAccountFixtures;
import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Top-ups through the real sessions: to your own wallet only, and never approved by yourself. */
@SpringBootTest
@Transactional
class TopUpControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private AccountRepository accounts;
  @Autowired private AuthSessionService sessions;
  @MockitoBean private HederaPaymentGateway gateway;

  @BeforeEach
  void setUp() {
    TestAccountFixtures.ensure(users, accounts);
    when(gateway.isLive()).thenReturn(true);
  }

  private MockMvc as(String userId) {
    String token = sessions.create(users.findById(userId).orElseThrow());
    return MockMvcBuilders.webAppContextSetup(context)
        .defaultRequest(get("/").header("Authorization", "Bearer " + token))
        .build();
  }

  private String askTopUp(String userId, String body) throws Exception {
    String json = as(userId)
        .perform(post("/api/v1/payments/top-up").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("TOP_UP"))
        .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
        .andReturn().getResponse().getContentAsString();
    return new ObjectMapper().readTree(json).path("id").asText();
  }

  @Test
  void the_destination_is_always_the_requesters_own_wallet() throws Exception {
    // A destination in the request is ignored: the wallet comes from the account.
    as("user_demo")
        .perform(post("/api/v1/payments/top-up").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":\"5\",\"destination\":\"0.0.666\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.destination").value("0.0.100001"));
  }

  @Test
  void an_admin_cannot_approve_their_own_top_up() throws Exception {
    String id = askTopUp("admin_demo", "{\"amount\":\"3\"}");

    as("admin_demo").perform(post("/api/v1/payments/" + id + "/approve")).andExpect(status().isForbidden());
  }

  @Test
  void a_user_cannot_approve_a_top_up_at_all() throws Exception {
    String id = askTopUp("user_demo", "{\"amount\":\"3\"}");

    as("user_demo").perform(post("/api/v1/payments/" + id + "/approve")).andExpect(status().isForbidden());
  }

  @Test
  void the_status_says_whose_wallet_payments_leave_from() throws Exception {
    as("user_demo").perform(get("/api/v1/payments/status"))
        .andExpect(jsonPath("$.paysFromUserWallets").value(true));
  }
}
