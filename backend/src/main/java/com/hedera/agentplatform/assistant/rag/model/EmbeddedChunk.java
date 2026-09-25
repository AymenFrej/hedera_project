package com.hedera.agentplatform.assistant.rag.model;

public record EmbeddedChunk(DocumentChunk chunk, float[] embedding) {}
