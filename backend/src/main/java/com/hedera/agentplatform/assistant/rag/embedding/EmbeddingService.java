package com.hedera.agentplatform.assistant.rag.embedding;

import java.util.List;

public interface EmbeddingService {
    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embedQuery(text);
    }

    default float[] embedQuery(String text) {
        List<float[]> vectors = embed(List.of(text));
        if (vectors.size() != 1) {
            throw new IllegalStateException("Embedding provider returned an unexpected number of vectors");
        }
        return vectors.getFirst();
    }
}
