package com.hedera.agentplatform.assistant.rag.store;

import com.hedera.agentplatform.assistant.rag.model.EmbeddedChunk;
import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import java.util.List;

public interface VectorStore {
    void replaceAll(List<EmbeddedChunk> chunks);

    boolean isEmpty();

    List<RetrievedChunk> search(float[] queryVector, int topK, double minimumScore);
}
