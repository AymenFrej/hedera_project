package com.hedera.agentplatform.payments.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.TestAccountFixtures;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class PaymentControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private AccountRepository accounts;
  @Autowired private AuthSessionService sessions;

  /** Signed in as the seeded USER (V7 demo login), through the real session and interceptor. */
  private MockMvc mockMvc;

  private MockMvc as(String userId) {
    String token = sessions.create(users.findById(userId).orElseThrow());
    return MockMvcBuilders.webAppContextSetup(context)
        .defaultRequest(get("/").header("Authorization", "Bearer " + token))
        .build();
  }

  @BeforeEach
  void setUp() {
    TestAccountFixtures.ensure(users, accounts);
    mockMvc = as("user_demo");
  }

  @Test
  void the_policy_engine_blocks_a_payment_without_an_envelope() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"0.0.4242\",\"amount\":\"10\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.policyVerdict").value("DENY"))
        .andExpect(jsonPath("$.policyRuleId").value("envelope.unknown"))
        .andExpect(jsonPath("$.transactionId").doesNotExist());
  }

  @Test
  void refuses_an_envelope_that_does_not_exist() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"0.0.4242\",\"amount\":\"10\",\"envelope\":\"holidays\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refuses_a_destination_that_is_not_an_account_id() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"alice\",\"amount\":\"10\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refuses_a_negative_amount() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"0.0.4242\",\"amount\":\"-5\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void approving_a_payment_that_is_not_held_is_a_conflict() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"destination\":\"0.0.4242\",\"amount\":\"1\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    as("admin_demo").perform(post("/api/v1/payments/" + id + "/approve")).andExpect(status().isConflict());
  }

  @Test
  void without_signing_in_payments_are_refused() throws Exception {
    MockMvcBuilders.webAppContextSetup(context).build()
        .perform(get("/api/v1/payments"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void a_user_cannot_answer_a_held_payment_not_even_their_own() throws Exception {
    mockMvc.perform(post("/api/v1/payments/any/approve")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/payments/any/reject")).andExpect(status().isForbidden());
  }

  @Test
  void a_user_sees_only_their_own_payments_an_admin_sees_all() throws Exception {
    String json =
        mockMvc
            .perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"0.0.4242\",\"amount\":\"1\"}"))
            .andReturn().getResponse().getContentAsString();
    String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    mockMvc.perform(get("/api/v1/payments/" + id)).andExpect(status().isOk());
    as("auditor_demo").perform(get("/api/v1/payments/" + id)).andExpect(status().isForbidden());
    as("admin_demo").perform(get("/api/v1/payments/" + id)).andExpect(status().isOk());
    as("admin_demo")
        .perform(get("/api/v1/payments"))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());
  }

  @Test
  void a_payment_is_attributed_to_the_signed_in_user() throws Exception {
    mockMvc
        .perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
            .content("{\"destination\":\"0.0.4242\",\"amount\":\"1\"}"))
        .andExpect(jsonPath("$.requestedByType").value("USER"))
        .andExpect(jsonPath("$.requestedById").value("user_demo"));
  }

  @Test
  void reports_that_the_ledger_is_not_active_without_credentials() throws Exception {
    mockMvc
        .perform(get("/api/v1/payments/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerActive").value(false));
  }
}
