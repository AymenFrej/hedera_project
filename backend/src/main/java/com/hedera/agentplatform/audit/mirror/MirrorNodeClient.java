package com.hedera.agentplatform.audit.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads messages back from the Hedera Mirror Node REST API.
 *
 * <p>Deliberately not the SDK: free SDK queries are throttled on testnet and answer BUSY, and
 * reading the proof through a different channel than the one that wrote it is what makes the
 * verification worth anything.
 */
@Component
public class MirrorNodeClient {

  private static final Logger log = LoggerFactory.getLogger(MirrorNodeClient.class);

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Fetches one message of a topic by sequence number.
   *
   * @return the decoded message content, or empty when the Mirror Node does not have it (yet)
   */
  public Optional<MirrorMessage> findMessage(String baseUrl, String topicId, long sequenceNumber) {
    String url =
        "%s/api/v1/topics/%s/messages/%d"
            .formatted(trimTrailingSlash(baseUrl), topicId, sequenceNumber);
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(20))
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 404) {
        // Mirror nodes lag a second or two behind consensus; this is not an error.
        return Optional.empty();
      }
      if (response.statusCode() != 200) {
        log.warn("Mirror Node returned HTTP {} for {}", response.statusCode(), url);
        return Optional.empty();
      }

      JsonNode node = objectMapper.readTree(response.body());
      String encoded = node.path("message").asText(null);
      if (encoded == null) {
        return Optional.empty();
      }

      String content = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
      return Optional.of(
          new MirrorMessage(
              content,
              node.path("consensus_timestamp").asText(null),
              node.path("payer_account_id").asText(null),
              node.path("sequence_number").asLong()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Mirror Node lookup failed for {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  /** One message as the Mirror Node sees it. */
  public record MirrorMessage(
      String content, String consensusTimestamp, String payerAccountId, long sequenceNumber) {}
}
