package com.hedera.agentplatform.payments.language;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the HTTP-based models share: the {@link ExtractedIntent} schema, built from the record so
 * every model gets the same fields and descriptions as Claude, and one JSON POST.
 */
final class ModelJson {

  static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private ModelJson() {}

  /** JSON Schema of {@link ExtractedIntent}; Gemini wants its type names in upper case. */
  static Map<String, Object> intentSchema(boolean upperCaseTypes) {
    return schemaOf(ExtractedIntent.class, upperCaseTypes);
  }

  /** JSON Schema of a record of booleans and strings, with its {@link JsonPropertyDescription}s. */
  static Map<String, Object> schemaOf(Class<?> type, boolean upperCaseTypes) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> names = new ArrayList<>();
    for (RecordComponent c : type.getRecordComponents()) {
      String kind = c.getType() == boolean.class ? "boolean" : "string";
      Map<String, Object> property = new LinkedHashMap<>();
      property.put("type", upperCaseTypes ? kind.toUpperCase(Locale.ROOT) : kind);
      JsonPropertyDescription description = c.getAccessor().getAnnotation(JsonPropertyDescription.class);
      if (description != null) {
        property.put("description", description.value());
      }
      properties.put(c.getName(), property);
      names.add(c.getName());
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", upperCaseTypes ? "OBJECT" : "object");
    schema.put("properties", properties);
    schema.put("required", names);
    return schema;
  }

  /** The model's JSON answer as an intent, or an {@link IntentExtractor.ExtractionException}. */
  static ExtractedIntent parseIntent(String text) {
    return parse(text, ExtractedIntent.class);
  }

  static <T> T parse(String text, Class<T> type) {
    try {
      return JSON.readValue(text, type);
    } catch (IOException e) {
      throw new IntentExtractor.ExtractionException("The language model did not return a valid request");
    }
  }

  record Reply(int status, JsonNode body) {}

  static Reply post(URI uri, Map<String, String> headers, Object body, Duration timeout)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    headers.forEach(request::header);
    HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode json;
    try {
      json = JSON.readTree(response.body());
    } catch (IOException e) {
      json = JSON.createObjectNode();
    }
    return new Reply(response.statusCode(), json);
  }
}
