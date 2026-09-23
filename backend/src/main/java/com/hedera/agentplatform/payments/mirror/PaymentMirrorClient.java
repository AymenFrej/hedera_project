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

  /**
   * Current balances of an account: HBAR, and every token it is associated with (up to 100), with
   * symbol and decimals so amounts can be shown as the user would write them.
   */
  public BalanceLookup findBalances(String accountId) {
    String base = trimTrailingSlash(properties.getMirrorNodeUrl());
    try {
      HttpResponse<String> account = get(base + "/api/v1/accounts/" + accountId + "?transactions=false");
      if (account.statusCode() == 404) {
        return new BalanceLookup(LookupState.NOT_FOUND, null);
      }
      if (account.statusCode() != 200) {
        log.warn("Mirror Node returned HTTP {} for account {}", account.statusCode(), accountId);
        return new BalanceLookup(LookupState.UNAVAILABLE, null);
      }
      JsonNode balance = JSON.readTree(account.body()).path("balance");

      HttpResponse<String> relations =
          get(base + "/api/v1/accounts/" + accountId + "/tokens?limit=100");
      if (relations.statusCode() != 200) {
        log.warn("Mirror Node returned HTTP {} for tokens of {}", relations.statusCode(), accountId);
        return new BalanceLookup(LookupState.UNAVAILABLE, null);
      }
      List<TokenHolding> tokens = new ArrayList<>();
      for (JsonNode t : JSON.readTree(relations.body()).path("tokens")) {
        String tokenId = t.path("token_id").asText();
        JsonNode info = tokenInfo(base, tokenId);
        tokens.add(
            new TokenHolding(
                tokenId,
                info == null ? null : info.path("symbol").asText(null),
                info == null ? null : info.path("name").asText(null),
                info == null ? t.path("decimals").asInt(0) : info.path("decimals").asInt(0),
                t.path("balance").asLong()));
      }
      return new BalanceLookup(
          LookupState.FOUND,
          new AccountBalances(
              accountId,
              balance.path("balance").asLong(),
              balance.path("timestamp").asText(null),
              tokens));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new BalanceLookup(LookupState.UNAVAILABLE, null);
    } catch (Exception e) {
      log.warn("Mirror Node balance lookup failed for {}: {}", accountId, e.getMessage());
      return new BalanceLookup(LookupState.UNAVAILABLE, null);
    }
  }

  /** Token name, symbol and decimals; null when the Mirror Node does not answer. */
  private JsonNode tokenInfo(String base, String tokenId) throws Exception {
    HttpResponse<String> response = get(base + "/api/v1/tokens/" + tokenId);
    return response.statusCode() == 200 ? JSON.readTree(response.body()) : null;
  }

  private HttpResponse<String> get(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

  /**
   * @param tinybars HBAR balance in tinybars
   * @param timestamp consensus timestamp the balance was computed at, {@code seconds.nanos}
   */
  public record AccountBalances(
      String accountId, long tinybars, String timestamp, List<TokenHolding> tokens) {}

  /** @param balance in the token's smallest unit */
  public record TokenHolding(
      String tokenId, String symbol, String name, int decimals, long balance) {}

  public record BalanceLookup(LookupState state, AccountBalances balances) {}

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
