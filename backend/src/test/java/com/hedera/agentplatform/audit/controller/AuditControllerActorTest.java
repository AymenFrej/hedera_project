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

    @Test
    void an_actor_sent_by_the_client_is_ignored() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

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
                        .perform(post("/api/v1/audit").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        AuditEventResponse response = objectMapper.readValue(json, AuditEventResponse.class);

        assertThat(response.actorType()).isEqualTo(ActorType.SYSTEM.name());
        assertThat(response.actorId()).isEqualTo("platform");
        assertThat(response.actorHederaAccountId()).isNull();
    }
}
