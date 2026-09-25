package com.hedera.agentplatform.assistant.service;

import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterAssistantTextGenerator implements AssistantTextGenerator {
    private static final String INSTRUCTIONS = """
        You are a Hedera-focused informational assistant. Use only facts directly supported by the
        retrieved official Hedera documentation. Do not add unsupported facts, numbers, examples,
        comparisons, or background from memory. If the context does not answer the question, state
        that the indexed documentation is insufficient. Keep the answer narrow when context is
        limited and cite supported claims using the source title and URL included with each excerpt.
        You may distinguish a general explanation from claims directly supported by sources.
        You do not have access to Hedera accounts or transactions and cannot execute operations.
        Never claim that any action was executed.
        """;

    private final OpenRouterGateway openRouter;
    private final String model;

    public OpenRouterAssistantTextGenerator(
            OpenRouterGateway openRouter,
            @Value("${openrouter.model:openrouter/free}") String configuredModel) {
        this.openRouter = openRouter;
        this.model = configuredModel == null || configuredModel.isBlank()
                ? "openrouter/free"
                : configuredModel.trim();
    }

    @Override
    public String generate(String message, List<RetrievedChunk> context) {
        return openRouter.generate(model, INSTRUCTIONS, buildUserPrompt(message, context));
    }

    private String buildUserPrompt(String message, List<RetrievedChunk> context) {
        StringBuilder prompt = new StringBuilder("Retrieved Hedera documentation context:\n");
        if (context == null || context.isEmpty()) {
            prompt.append("No relevant documentation was retrieved. State that the available indexed documentation does not answer the question.\n");
        } else {
            for (RetrievedChunk result : context) {
                var chunk = result.chunk();
                prompt.append("\n[Source: ")
                        .append(chunk.title())
                        .append(" - ")
                        .append(chunk.section())
                        .append(" | ")
                        .append(chunk.source())
                        .append("]\n")
                        .append(chunk.text())
                        .append('\n');
            }
        }
        prompt.append("\nUser question: ").append(message);
        return prompt.toString();
    }
}
