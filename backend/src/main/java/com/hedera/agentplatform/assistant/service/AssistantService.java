package com.hedera.agentplatform.assistant.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.assistant.dto.ChatRequest;
import com.hedera.agentplatform.assistant.dto.ChatResponse;
import com.hedera.agentplatform.assistant.dto.ChatSource;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import com.hedera.agentplatform.assistant.rag.retrieval.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantService {
    private static final Logger logger = LoggerFactory.getLogger(AssistantService.class);

    private final AuthSessionService sessions;
    private final AssistantTextGenerator textGenerator;
    private final Retriever retriever;

    public AssistantService(AuthSessionService sessions, AssistantTextGenerator textGenerator, Retriever retriever) {
        this.sessions = sessions;
        this.textGenerator = textGenerator;
        this.retriever = retriever;
    }

    public ChatResponse chat(String authorization, ChatRequest request) {
        sessions.require(authorization);
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("A message is required");
        }

        String message = request.message().trim();
        try {
            var retrieved = retriever.retrieve(message);
            String response = textGenerator.generate(message, retrieved);
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("The assistant returned no text");
            }
            var sources = retrieved.stream()
                    .map(RetrievedChunk::chunk)
                    .map(chunk -> new ChatSource(chunk.title(), chunk.source()))
                    .distinct()
                    .toList();
            return new ChatResponse(response.trim(), sources);
        } catch (RuntimeException exception) {
            logProviderFailure(exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The assistant is temporarily unavailable. Please try again later.");
        }
    }

    private void logProviderFailure(RuntimeException exception) {
        if (exception instanceof OpenRouterProviderException providerException) {
            logger.warn(
                    "Assistant provider request failed (exceptionType={}, providerStatus={}, providerCode={})",
                    exception.getClass().getSimpleName(),
                    providerException.statusCode(),
                    providerException.providerCode());
            return;
        }

        logger.warn("Assistant provider request failed (exceptionType={})", exception.getClass().getSimpleName());
    }

}
