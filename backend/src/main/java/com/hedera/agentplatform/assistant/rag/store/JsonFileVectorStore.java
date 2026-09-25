package com.hedera.agentplatform.assistant.rag.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A local vector index persisted as JSON so indexed documentation survives backend restarts. */
@Component
public class JsonFileVectorStore implements VectorStore {
    private static final Logger logger = LoggerFactory.getLogger(JsonFileVectorStore.class);
    private static final TypeReference<List<EmbeddedChunk>> INDEX_TYPE = new TypeReference<>() {};

    private final AtomicReference<List<EmbeddedChunk>> index = new AtomicReference<>(List.of());
    private final ObjectMapper objectMapper;
    private final Path indexFile;

    public JsonFileVectorStore(
            ObjectMapper objectMapper,
            @Value("${assistant.rag.index-path:data/hedera-rag-index.json}") String indexPath) {
        this.objectMapper = objectMapper;
        this.indexFile = Path.of(indexPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void loadFromDisk() {
        if (!Files.isRegularFile(indexFile)) {
            logger.info("No persisted Hedera RAG index found (path={}); index documentation once to create it", indexFile);
            return;
        }
        try {
            List<EmbeddedChunk> saved = objectMapper.readValue(indexFile.toFile(), INDEX_TYPE);
            index.set(saved == null ? List.of() : List.copyOf(saved));
            logger.info("Loaded persisted Hedera RAG index (chunks={}, path={})", index.get().size(), indexFile);
        } catch (IOException | RuntimeException exception) {
            logger.error("Could not load persisted Hedera RAG index (path={}, exceptionType={}); reindex documentation",
                    indexFile, exception.getClass().getSimpleName());
        }
    }

    @Override
    public synchronized void replaceAll(List<EmbeddedChunk> chunks) {
        List<EmbeddedChunk> replacement = chunks == null ? List.of() : List.copyOf(chunks);
        persist(replacement);
        index.set(replacement);
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
                .filter(entry -> entry.embedding() != null && entry.embedding().length == queryVector.length)
                .map(entry -> new RetrievedChunk(entry.chunk(), cosineSimilarity(queryVector, entry.embedding())))
                .filter(result -> Double.isFinite(result.score()) && result.score() >= minimumScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private void persist(List<EmbeddedChunk> replacement) {
        Path parent = indexFile.getParent();
        Path temporaryFile = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            objectMapper.writeValue(temporaryFile.toFile(), replacement);
            try {
                Files.move(temporaryFile, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                logger.warn("Could not remove temporary RAG index file (path={})", temporaryFile);
            }
            throw new IllegalStateException("Could not persist the local Hedera RAG index", exception);
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int vectorIndex = 0; vectorIndex < left.length; vectorIndex++) {
            dot += left[vectorIndex] * right[vectorIndex];
            leftNorm += left[vectorIndex] * left[vectorIndex];
            rightNorm += right[vectorIndex] * right[vectorIndex];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
