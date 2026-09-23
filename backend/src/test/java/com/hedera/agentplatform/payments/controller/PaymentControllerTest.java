package com.hedera.agentplatform.payments.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void creates_a_payment_with_the_default_policy() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"0.0.4242\",\"amount\":\"10\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SIMULATED"))
        .andExpect(jsonPath("$.policyRuleId").value("policy.none"));
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

    mockMvc.perform(post("/api/v1/payments/" + id + "/approve")).andExpect(status().isConflict());
  }

  @Test
  void reports_that_the_ledger_is_not_active_without_credentials() throws Exception {
    mockMvc
        .perform(get("/api/v1/payments/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerActive").value(false));
  }
}
