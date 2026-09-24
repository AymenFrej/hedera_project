package com.hedera.agentplatform.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.shared.model.ActorType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A client must not be able to declare who performed an action: an audit trail whose subject is
 * chosen by the caller proves nothing. The request DTO has no actor field, so an actor sent in the
 * body is ignored and the server-side attribution wins.
 */
@SpringBootTest
@Transactional
class AuditControllerActorTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.hedera.agentplatform.accounts.auth.AuthSessionService sessions;
    @Autowired private com.hedera.agentplatform.accounts.repository.UserRepository users;

    @Test
    void an_actor_sent_by_the_client_is_ignored() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        var admin = new com.hedera.agentplatform.accounts.entity.UserEntity();
        admin.id = "audit-test-admin";
        admin.email = "audit-test-admin@example.test";
        admin.displayName = "Audit test admin";
        admin.role = "ADMIN";
        admin.accountId = "audit-test-wallet";
        admin.passwordHash = "not-a-login-password";
        users.save(admin);
        String authorization = "Bearer " + sessions.create(admin);

        String body =
                """
                {
                  "agent": "PaymentAgent",
                  "action": "TRANSFER",
                  "status": "SUCCESS",
                  "actor": { "type": "USER", "id": "attacker", "hederaAccountId": "0.0.666" },
                  "actorId": "attacker",
                  "metadata": { "amount": "50" }
                }
                """;

        String json =
                mockMvc
                        .perform(post("/api/v1/audit").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        AuditEventResponse response = objectMapper.readValue(json, AuditEventResponse.class);

        assertThat(response.actorType()).isEqualTo(ActorType.USER.name());
        assertThat(response.actorId()).isEqualTo(admin.id);
        assertThat(response.actorHederaAccountId()).isNull();
    }
}
