package com.hedera.agentplatform.assistant.rag.embedding;

import com.hedera.agentplatform.assistant.service.OpenRouterGateway;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterEmbeddingService implements EmbeddingService {
    private static final int MAX_BATCH_SIZE = 100;
    private final OpenRouterGateway openRouter;
    private final String model;

    public OpenRouterEmbeddingService(
            OpenRouterGateway openRouter,
            @Value("${openrouter.embedding-model:nvidia/nemotron-3-embed-1b:free}") String configuredModel) {
        this.openRouter = openRouter;
        this.model = configuredModel == null || configuredModel.isBlank()
                ? "nvidia/nemotron-3-embed-1b:free"
                : configuredModel.trim();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + MAX_BATCH_SIZE, texts.size()));
            List<float[]> batchVectors = openRouter.embed(model, batch);
            validateResult(batch.size(), batchVectors);
            vectors.addAll(batchVectors);
        }
        return List.copyOf(vectors);
    }

    private void validateResult(int expectedCount, List<float[]> vectors) {
        if (vectors == null || vectors.size() != expectedCount
                || vectors.stream().anyMatch(vector -> vector == null || vector.length == 0)) {
            throw new IllegalStateException("OpenRouter returned incomplete embedding data");
        }
    }
}
