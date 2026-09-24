package com.hedera.agentplatform.payments.language;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reads payment sentences with Google Gemini (the free tier needs a Google account, no card). The
 * key goes in a header, never in the URL. The answer is constrained to the {@link ExtractedIntent}
 * schema and, like every model, is only a proposal the Payments module resolves and checks.
 */
public class GeminiIntentExtractor implements IntentExtractor {

  static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private final String baseUrl;
  private final String apiKey;
  private final String model;

  public GeminiIntentExtractor(String baseUrl, String apiKey, String model) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.apiKey = apiKey;
    this.model = model;
  }

  @Override
  public ExtractedIntent extract(String sentence) {
    Map<String, Object> body =
        Map.of(
            "systemInstruction",
                Map.of("parts", List.of(Map.of("text", ClaudeIntentExtractor.INSTRUCTIONS))),
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", sentence)))),
            "generationConfig",
                Map.of(
                    "temperature", 0,
                    "responseMimeType", "application/json",
                    "responseSchema", ModelJson.intentSchema(true)));
    ModelJson.Reply reply;
    try {
      reply =
          ModelJson.post(
              URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"),
              Map.of("x-goog-api-key", apiKey),
              body,
              TIMEOUT);
    } catch (HttpTimeoutException e) {
      throw new ExtractionException("Gemini took too long to answer: try again", e);
    } catch (IOException e) {
      throw new ExtractionException("Gemini could not be reached", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExtractionException("Interrupted while waiting for Gemini", e);
    }

    JsonNode json = reply.body();
    if (reply.status() == 429) {
      throw new ExtractionException(
          "The free Gemini quota is used up for now: wait a minute, or use the manual request");
    }
    if (reply.status() != 200) {
      throw new ExtractionException(
          "Gemini answered " + reply.status() + ": " + json.path("error").path("message").asText("no detail"));
    }
    JsonNode candidate = json.path("candidates").path(0);
    String finish = candidate.path("finishReason").asText("");
    if (!json.path("promptFeedback").path("blockReason").isMissingNode()
        || finish.equals("SAFETY")
        || finish.equals("PROHIBITED_CONTENT")) {
      throw new ExtractionException("The language model declined this request");
    }
    String text = candidate.path("content").path("parts").path(0).path("text").asText("");
    if (text.isBlank()) {
      throw new ExtractionException("Gemini returned no answer");
    }
    return ModelJson.parseIntent(text);
  }

  @Override
  public String source() {
    return model + " (Gemini)";
  }
}
