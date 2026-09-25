package com.hedera.agentplatform.assistant.dto;

import java.util.List;

public record ChatResponse(String message, List<ChatSource> sources) {
    public ChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
