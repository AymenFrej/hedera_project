package com.hedera.agentplatform.assistant;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import com.hedera.agentplatform.assistant.service.AssistantTextGenerator;
import com.hedera.agentplatform.assistant.rag.retrieval.Retriever;
import com.hedera.agentplatform.assistant.rag.service.RagIndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssistantEndpointTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @MockitoBean AssistantTextGenerator textGenerator;
    @MockitoBean Retriever retriever;
    @MockitoBean RagIndexingService indexingService;

    @org.junit.jupiter.api.BeforeEach
    void configureAssistant() {
        when(textGenerator.generate(org.mockito.ArgumentMatchers.eq("What is Hedera?"), anyList()))
                .thenReturn("A generated Hedera answer.");
        when(retriever.retrieve("What is Hedera?")).thenReturn(java.util.List.of());
    }

    @Test
    void indexing_endpoint_is_admin_only() throws Exception {
        String userToken = "Bearer " + sessions.create(users.findById("user_demo").orElseThrow());
        mvc.perform(post("/api/v1/admin/assistant/rag/index").header("Authorization", userToken))
                .andExpect(status().isForbidden());

        String adminToken = "Bearer " + sessions.create(users.findById("admin_demo").orElseThrow());
        when(indexingService.index()).thenReturn(12);
        mvc.perform(post("/api/v1/admin/assistant/rag/index").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedChunks").value(12));
        verify(indexingService).index();
    }

    @Test
    void authenticated_assistant_roles_receive_mock_chat_response() throws Exception {
        for (String role : new String[]{"USER", "ADMIN", "AUDITOR", "PLATFORM"}) {
            var user = users.findById(switch (role) {
                case "USER" -> "user_demo";
                case "ADMIN" -> "admin_demo";
                case "AUDITOR" -> "auditor_demo";
                default -> "platform_demo";
            }).orElseThrow();
            String token = sessions.create(user);

            mvc.perform(post("/api/v1/assistant/chat")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"What is Hedera?\",\"userId\":\"forged-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A generated Hedera answer."))
                .andExpect(jsonPath("$.userId").doesNotExist());
        }
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        mvc.perform(post("/api/v1/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What is Hedera?\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void blank_message_is_rejected() throws Exception {
        String token = "Bearer " + sessions.create(users.findById("user_demo").orElseThrow());

        mvc.perform(post("/api/v1/assistant/chat")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }
}
