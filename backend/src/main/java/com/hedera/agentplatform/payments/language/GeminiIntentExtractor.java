package com.hedera.agentplatform.payments.language;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  private final Duration retryDelay;

  public GeminiIntentExtractor(String baseUrl, String apiKey, String model) {
    this(baseUrl, apiKey, model, Duration.ofMillis(1500));
  }

  GeminiIntentExtractor(String baseUrl, String apiKey, String model, Duration retryDelay) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.apiKey = apiKey;
    this.model = model;
    this.retryDelay = retryDelay;
  }

  @Override
  public ExtractedIntent extract(String sentence) {
    return generate(ClaudeIntentExtractor.INSTRUCTIONS, List.of(Map.of("text", sentence)));
  }

  @Override
  public Set<String> documentTypes() {
    Set<String> types = new HashSet<>(DocumentPrompt.TEXT_TYPES);
    types.add(DocumentPrompt.PDF);
    types.addAll(DocumentPrompt.IMAGE_TYPES);
    return types;
  }

  @Override
  public ExtractedIntent extractFromDocument(Document document, String note) {
    List<Map<String, Object>> parts =
        document.isText()
            ? List.of(Map.of("text", DocumentPrompt.textDocument(document, note)))
            : List.of(
                Map.of(
                    "inlineData",
                    Map.of(
                        "mimeType", document.mimeType(),
                        "data", Base64.getEncoder().encodeToString(document.data()))),
                Map.of("text", DocumentPrompt.fileNote(document, note)));
    return generate(DocumentPrompt.INSTRUCTIONS, parts);
  }

  private ExtractedIntent generate(String instructions, List<Map<String, Object>> parts) {
    Map<String, Object> body =
        Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
            "contents", List.of(Map.of("role", "user", "parts", parts)),
            "generationConfig",
                Map.of(
                    "temperature", 0,
                    "responseMimeType", "application/json",
                    "responseSchema", ModelJson.intentSchema(true)));
    ModelJson.Reply reply;
    try {
      reply = send(body);
      // A busy model usually clears within a second or two: one more try before giving up.
      if (reply.status() == 503) {
        Thread.sleep(retryDelay.toMillis());
        reply = send(body);
      }
    } catch (HttpTimeoutException e) {
      throw new ExtractionException("Gemini took too long to answer: try again", e);
    } catch (IOException e) {
      throw new ExtractionException("Gemini could not be reached", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExtractionException("Interrupted while waiting for Gemini", e);
    }

    JsonNode json = reply.body();
    if (reply.status() == 503) {
      throw new ExtractionException(
          "Gemini is busy right now: try again in a moment, or use the manual request");
    }
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

  private ModelJson.Reply send(Map<String, Object> body) throws IOException, InterruptedException {
    return ModelJson.post(
        URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"),
        Map.of("x-goog-api-key", apiKey),
        body,
        TIMEOUT);
  }

  @Override
  public String source() {
    return model + " (Gemini)";
  }
}
