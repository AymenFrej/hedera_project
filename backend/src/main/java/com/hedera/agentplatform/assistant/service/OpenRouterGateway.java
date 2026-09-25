package com.hedera.agentplatform.assistant.service;

import java.util.List;

/** Provider boundary for OpenRouter chat completions and embeddings. */
public interface OpenRouterGateway {
    String generate(String model, String instructions, String prompt);

    List<float[]> embed(String model, List<String> texts);
}
