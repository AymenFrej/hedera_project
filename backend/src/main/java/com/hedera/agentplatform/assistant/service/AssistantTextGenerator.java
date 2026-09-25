package com.hedera.agentplatform.assistant.service;

import com.hedera.agentplatform.assistant.rag.model.RetrievedChunk;
import java.util.List;

public interface AssistantTextGenerator {
    String generate(String message, List<RetrievedChunk> context);
}
