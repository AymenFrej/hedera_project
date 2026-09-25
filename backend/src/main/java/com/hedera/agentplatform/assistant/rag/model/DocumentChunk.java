package com.hedera.agentplatform.assistant.rag.model;

public record DocumentChunk(String chunkId, String text, String source, String title, String section) {}
