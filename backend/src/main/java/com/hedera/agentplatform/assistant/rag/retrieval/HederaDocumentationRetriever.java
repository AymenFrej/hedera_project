package com.hedera.agentplatform.assistant.rag.retrieval;

import com.hedera.agentplatform.assistant.rag.embedding.EmbeddingService;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import com.hedera.agentplatform.assistant.rag.store.VectorStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HederaDocumentationRetriever implements Retriever {
    private final EmbeddingService embeddings;
    private final VectorStore vectorStore;
    private final int topK;
    private final double minimumScore;

    public HederaDocumentationRetriever(
            EmbeddingService embeddings,
            VectorStore vectorStore,
            @Value("${assistant.rag.top-k:5}") int topK,
            @Value("${assistant.rag.minimum-score:0.20}") double minimumScore) {
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.minimumScore = minimumScore;
    }

    @Override
    public List<RetrievedChunk> retrieve(String question) {
        if (vectorStore.isEmpty()) {
            return List.of();
        }
        return vectorStore.search(embeddings.embedQuery(question), topK, minimumScore);
    }
}
