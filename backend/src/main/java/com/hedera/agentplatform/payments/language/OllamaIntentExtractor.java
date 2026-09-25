package com.hedera.agentplatform.payments.language;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads payment sentences with a model running locally in Ollama: free, no account, and nothing
 * leaves the machine. The answer is constrained to the {@link ExtractedIntent} schema, and like
 * every model it is only a proposal the Payments module resolves and checks.
 */
public class OllamaIntentExtractor implements IntentExtractor {

  // A small model on a laptop CPU can take a while, above all on the first call when it loads.
  private static final Duration TIMEOUT = Duration.ofSeconds(120);

  private final String baseUrl;
  private final String model;

  public OllamaIntentExtractor(String baseUrl, String model) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.model = model;
  }

  @Override
  public ExtractedIntent extract(String sentence) {
    return chat(ClaudeIntentExtractor.INSTRUCTIONS, sentence);
  }

  @Override
  public <T> T extractAs(String instructions, String text, Class<T> type) {
    return ModelJson.parse(answer(instructions, text, ModelJson.schemaOf(type, false)), type);
  }

  /** Small local models read text only: PDFs and images need Gemini or Claude. */
  @Override
  public Set<String> documentTypes() {
    return DocumentPrompt.TEXT_TYPES;
  }

  @Override
  public ExtractedIntent extractFromDocument(Document document, String note) {
    if (!document.isText()) {
      throw new ExtractionException(source() + " reads text documents only");
    }
    return chat(DocumentPrompt.INSTRUCTIONS, DocumentPrompt.textDocument(document, note));
  }

  private ExtractedIntent chat(String instructions, String userContent) {
    return ModelJson.parseIntent(answer(instructions, userContent, ModelJson.intentSchema(false)));
  }

  /** The model's JSON text, constrained to {@code schema}. */
  private String answer(String instructions, String userContent, Map<String, Object> schema) {
    Map<String, Object> body =
        Map.of(
            "model", model,
            "stream", false,
            "format", schema,
            "options", Map.of("temperature", 0),
            "messages",
                List.of(
                    Map.of("role", "system", "content", instructions),
                    Map.of("role", "user", "content", userContent)));
    ModelJson.Reply reply;
    try {
      reply = ModelJson.post(URI.create(baseUrl + "/api/chat"), Map.of(), body, TIMEOUT);
    } catch (ConnectException e) {
      throw new ExtractionException(
          "Ollama is not running at " + baseUrl + ": start the Ollama app, then try again", e);
    } catch (HttpTimeoutException e) {
      throw new ExtractionException("The local model took too long to answer: try again", e);
    } catch (IOException e) {
      throw new ExtractionException("Ollama could not be reached at " + baseUrl, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExtractionException("Interrupted while waiting for the local model", e);
    }

    JsonNode json = reply.body();
    if (reply.status() == 404) {
      throw new ExtractionException(
          "The model " + model + " is not installed in Ollama: run \"ollama pull " + model + "\"");
    }
    if (reply.status() != 200) {
      throw new ExtractionException(
          "Ollama answered " + reply.status() + ": " + json.path("error").asText("no detail"));
    }
    String content = json.path("message").path("content").asText("");
    if (content.isBlank()) {
      throw new ExtractionException("The local model returned no answer");
    }
    return content;
  }

  @Override
  public String source() {
    return model + " (Ollama, local)";
  }
}
