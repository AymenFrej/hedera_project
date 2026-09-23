package com.hedera.agentplatform.payments.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads transfers back from the Hedera Mirror Node REST API.
 *
 * <p>Same reasoning as the audit module: free SDK queries are throttled on testnet, and checking a
 * transfer through a different channel than the one that sent it is what makes the check worth
 * anything.
 */
@Component
public class PaymentMirrorClient {

  private static final Logger log = LoggerFactory.getLogger(PaymentMirrorClient.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final HederaProperties properties;

  public PaymentMirrorClient(HederaProperties properties) {
    this.properties = properties;
  }

  /**
   * Looks a transaction up by id, e.g. {@code 0.0.5239440@1790194819.526000707}.
   *
   * <p>Distinguishes "the network has no such transaction" from "the Mirror Node could not be
   * asked": only the first one allows concluding anything.
   */
  public MirrorLookup findTransaction(String transactionId) {
    String url =
        "%s/api/v1/transactions/%s"
            .formatted(trimTrailingSlash(properties.getMirrorNodeUrl()), toMirrorId(transactionId));
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
        return MirrorLookup.notFound();
      }
      if (response.statusCode() != 200) {
        log.warn("Mirror Node returned HTTP {} for {}", response.statusCode(), url);
        return MirrorLookup.unavailable();
      }
      MirrorTransaction transaction = parse(response.body());
      return transaction == null ? MirrorLookup.notFound() : MirrorLookup.found(transaction);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return MirrorLookup.unavailable();
    } catch (Exception e) {
      log.warn("Mirror Node lookup failed for {}: {}", url, e.getMessage());
      return MirrorLookup.unavailable();
    }
  }

  /** Mirror Node ids use dashes: {@code 0.0.5239440-1790194819-526000707}. */
  static String toMirrorId(String transactionId) {
    int at = transactionId.indexOf('@');
    if (at < 0) {
      return transactionId;
    }
    return transactionId.substring(0, at) + "-" + transactionId.substring(at + 1).replace('.', '-');
  }

  /** Parses a {@code /api/v1/transactions/{id}} response; null when it holds no transaction. */
  static MirrorTransaction parse(String body) throws Exception {
    JsonNode transactions = JSON.readTree(body).path("transactions");
    if (!transactions.isArray() || transactions.isEmpty()) {
      return null;
    }
    // A scheduled or duplicate submission can add entries; the user-submitted one is not scheduled.
    JsonNode tx = transactions.get(0);
    for (JsonNode candidate : transactions) {
      if (!candidate.path("scheduled").asBoolean(false)) {
        tx = candidate;
        break;
      }
    }

    Map<String, Long> hbar = new LinkedHashMap<>();
    for (JsonNode t : tx.path("transfers")) {
      hbar.merge(t.path("account").asText(), t.path("amount").asLong(), Long::sum);
    }
    List<TokenTransfer> tokens = new ArrayList<>();
    for (JsonNode t : tx.path("token_transfers")) {
      tokens.add(
          new TokenTransfer(
              t.path("token_id").asText(), t.path("account").asText(), t.path("amount").asLong()));
    }
    return new MirrorTransaction(
        tx.path("result").asText(null),
        tx.path("name").asText(null),
        tx.path("consensus_timestamp").asText(null),
        hbar,
        tokens);
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  /**
   * @param result network result, e.g. SUCCESS or INSUFFICIENT_ACCOUNT_BALANCE
   * @param hbarTransfers net tinybar change per account, fees included
   */
  public record MirrorTransaction(
      String result,
      String name,
      String consensusTimestamp,
      Map<String, Long> hbarTransfers,
      List<TokenTransfer> tokenTransfers) {

    /** Net change of a token balance for one account. */
    public long tokenChange(String tokenId, String account) {
      return tokenTransfers.stream()
          .filter(t -> t.tokenId().equals(tokenId) && t.account().equals(account))
          .mapToLong(TokenTransfer::amount)
          .sum();
    }
  }

  public record TokenTransfer(String tokenId, String account, long amount) {}

  public enum LookupState {
    FOUND,
    NOT_FOUND,
    UNAVAILABLE
  }

  public record MirrorLookup(LookupState state, MirrorTransaction transaction) {
    public static MirrorLookup found(MirrorTransaction transaction) {
      return new MirrorLookup(LookupState.FOUND, transaction);
    }

    public static MirrorLookup notFound() {
      return new MirrorLookup(LookupState.NOT_FOUND, null);
    }

    public static MirrorLookup unavailable() {
      return new MirrorLookup(LookupState.UNAVAILABLE, null);
    }
  }
}
