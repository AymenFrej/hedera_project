package com.hedera.agentplatform.assistant.service;

public class OpenRouterProviderException extends RuntimeException {
    private final int statusCode;
    private final String providerCode;

    public OpenRouterProviderException(int statusCode, String providerCode) {
        super("OpenRouter request failed");
        this.statusCode = statusCode;
        this.providerCode = providerCode == null || providerCode.isBlank() ? "unknown" : providerCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String providerCode() {
        return providerCode;
    }
}
