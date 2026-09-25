package com.hedera.agentplatform.assistant.rag.retrieval;

import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import java.util.List;

public interface Retriever {
    List<RetrievedChunk> retrieve(String question);
}
