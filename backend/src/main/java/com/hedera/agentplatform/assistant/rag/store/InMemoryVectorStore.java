package com.hedera.agentplatform.assistant.rag.store;

import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryVectorStore implements VectorStore {
    private final AtomicReference<List<EmbeddedChunk>> index = new AtomicReference<>(List.of());

    @Override
    public void replaceAll(List<EmbeddedChunk> chunks) {
        index.set(chunks == null ? List.of() : List.copyOf(chunks));
    }

    @Override
    public boolean isEmpty() {
        return index.get().isEmpty();
    }

    @Override
    public List<RetrievedChunk> search(float[] queryVector, int topK, double minimumScore) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        return index.get().stream()
                .filter(entry -> entry.embedding().length == queryVector.length)
                .map(entry -> new RetrievedChunk(entry.chunk(), cosineSimilarity(queryVector, entry.embedding())))
                .filter(result -> Double.isFinite(result.score()) && result.score() >= minimumScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
