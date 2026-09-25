package com.hedera.agentplatform.assistant.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.assistant.dto.ChatRequest;
import com.hedera.agentplatform.assistant.rag.retrieval.Retriever;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {
    @Mock AuthSessionService sessions;
    @Mock AssistantTextGenerator textGenerator;
    @Mock Retriever retriever;
    @InjectMocks AssistantService service;

    @Test
    void returns_generated_provider_text() {
        when(sessions.require("Bearer session-token")).thenReturn(new UserEntity());
        when(retriever.retrieve("Explain Hedera")).thenReturn(java.util.List.of());
        when(textGenerator.generate("Explain Hedera", java.util.List.of()))
                .thenReturn("Hedera is a public distributed ledger.");

        var response = service.chat("Bearer session-token", new ChatRequest(" Explain Hedera "));

        assertThat(response.message()).isEqualTo("Hedera is a public distributed ledger.");
        verify(textGenerator).generate("Explain Hedera", java.util.List.of());
    }

    @Test
    void provider_failure_becomes_sanitized_service_unavailable_response() {
        when(sessions.require("Bearer session-token")).thenReturn(new UserEntity());
        when(retriever.retrieve("Explain Hedera")).thenReturn(java.util.List.of());
        when(textGenerator.generate("Explain Hedera", java.util.List.of()))
                .thenThrow(new IllegalStateException("provider key sk-sensitive-value"));

        assertThatThrownBy(() -> service.chat("Bearer session-token", new ChatRequest("Explain Hedera")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    var responseStatus = (ResponseStatusException) error;
                    assertThat(responseStatus.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatus.getReason()).contains("temporarily unavailable").doesNotContain("sk-sensitive-value");
                });
    }

    @Test
    void openrouter_rate_limit_becomes_sanitized_service_unavailable_response() {
        when(sessions.require("Bearer session-token")).thenReturn(new UserEntity());
        when(retriever.retrieve("Explain Hedera")).thenReturn(java.util.List.of());
        OpenRouterProviderException providerFailure = new OpenRouterProviderException(429, "rate_limit_exceeded");
        when(textGenerator.generate("Explain Hedera", java.util.List.of())).thenThrow(providerFailure);

        assertThatThrownBy(() -> service.chat("Bearer session-token", new ChatRequest("Explain Hedera")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    var responseStatus = (ResponseStatusException) error;
                    assertThat(responseStatus.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatus.getReason()).doesNotContain("quota reached");
                });
    }
}
