package com.hedera.agentplatform.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenRouterClient implements OpenRouterGateway {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenRouterClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${OPENROUTER_API_KEY:}") String apiKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public String generate(String model, String instructions, String prompt) {
        requireApiKey();
        ChatCompletionResponse response = post("/chat/completions", new ChatCompletionRequest(
                model,
                List.of(new Message("system", instructions), new Message("user", prompt)),
                2048),
                ChatCompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new IllegalStateException("OpenRouter returned an empty chat response");
        }
        return response.choices().getFirst().message().content();
    }

    @Override
    public List<float[]> embed(String model, List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        requireApiKey();
        EmbeddingResponse response = post("/embeddings", new EmbeddingRequest(model, texts), EmbeddingResponse.class);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("OpenRouter returned an empty embedding response");
        }
        List<EmbeddingItem> items = response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::index))
                .toList();
        return items.stream().map(EmbeddingItem::embedding).map(this::toVector).toList();
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return client.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException exception) {
            throw new OpenRouterProviderException(exception.getStatusCode().value(), providerCode(exception));
        }
    }

    private String providerCode(RestClientResponseException exception) {
        try {
            JsonNode error = objectMapper.readTree(exception.getResponseBodyAsByteArray()).path("error");
            JsonNode code = error.path("code");
            if (code.isMissingNode() || code.isNull()) code = error.path("type");
            if (code.isTextual() || code.isNumber()) {
                String safeCode = code.asText().replaceAll("[^A-Za-z0-9_.-]", "_");
                return safeCode.substring(0, Math.min(safeCode.length(), 80));
            }
        } catch (Exception ignored) {
            // Provider error bodies are not exposed or logged when they cannot be parsed.
        }
        return "unknown";
    }

    private void requireApiKey() {
        if (apiKey.isBlank()) throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
    }

    private float[] toVector(List<Float> values) {
        if (values == null || values.isEmpty()) return new float[0];
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) vector[index] = values.get(index);
        return vector;
    }

    private record ChatCompletionRequest(String model, List<Message> messages, int max_tokens) {}
    private record Message(String role, String content) {}
    private record ChatCompletionResponse(List<Choice> choices) {}
    private record Choice(Message message) {}
    private record EmbeddingRequest(String model, List<String> input) {}
    private record EmbeddingResponse(List<EmbeddingItem> data) {}
    private record EmbeddingItem(int index, List<Float> embedding) {}
}
