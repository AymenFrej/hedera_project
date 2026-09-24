package com.hedera.agentplatform.tokens.mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What the ledger says about a token, read from the Mirror Node REST API: its facts and keys, who
 * holds it, and what a transaction really did and cost. Every lookup distinguishes "the ledger has
 * no such thing" (NOT_FOUND) from "the Mirror Node could not be asked" (UNAVAILABLE).
 */
@Component
public class TokenMirrorClient {

  private static final Logger log = LoggerFactory.getLogger(TokenMirrorClient.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final HederaProperties properties;

  public TokenMirrorClient(HederaProperties properties) {
    this.properties = properties;
  }

  public FactsLookup facts(String tokenId) {
    try {
      HttpResponse<String> response = get("/api/v1/tokens/" + tokenId);
      if (response.statusCode() == 404) {
        return new FactsLookup(LookupState.NOT_FOUND, null);
      }
      if (response.statusCode() != 200) {
        return new FactsLookup(LookupState.UNAVAILABLE, null);
      }
      return new FactsLookup(LookupState.FOUND, parseFacts(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new FactsLookup(LookupState.UNAVAILABLE, null);
    } catch (Exception e) {
      log.warn("Mirror Node token lookup failed for {}: {}", tokenId, e.getMessage());
      return new FactsLookup(LookupState.UNAVAILABLE, null);
    }
  }

  /** The largest holders first, accounts holding zero left out. */
  public HoldersLookup holders(String tokenId, int limit) {
    try {
      HttpResponse<String> response =
          get("/api/v1/tokens/" + tokenId + "/balances?order=desc&limit=" + Math.min(limit, 100));
      if (response.statusCode() == 404) {
        return new HoldersLookup(LookupState.NOT_FOUND, List.of());
      }
      if (response.statusCode() != 200) {
        return new HoldersLookup(LookupState.UNAVAILABLE, List.of());
      }
      return new HoldersLookup(LookupState.FOUND, parseHolders(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HoldersLookup(LookupState.UNAVAILABLE, List.of());
    } catch (Exception e) {
      log.warn("Mirror Node holders lookup failed for {}: {}", tokenId, e.getMessage());
      return new HoldersLookup(LookupState.UNAVAILABLE, List.of());
    }
  }

  /** A transaction by id, e.g. {@code 0.0.5239440@1790194819.526000707}. */
  public TransactionLookup transaction(String transactionId) {
    try {
      HttpResponse<String> response = get("/api/v1/transactions/" + toMirrorId(transactionId));
      if (response.statusCode() == 404) {
        return new TransactionLookup(LookupState.NOT_FOUND, null);
      }
      if (response.statusCode() != 200) {
        return new TransactionLookup(LookupState.UNAVAILABLE, null);
      }
      TransactionFacts facts = parseTransaction(response.body());
      return facts == null
          ? new TransactionLookup(LookupState.NOT_FOUND, null)
          : new TransactionLookup(LookupState.FOUND, facts);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new TransactionLookup(LookupState.UNAVAILABLE, null);
    } catch (Exception e) {
      log.warn("Mirror Node transaction lookup failed for {}: {}", transactionId, e.getMessage());
      return new TransactionLookup(LookupState.UNAVAILABLE, null);
    }
  }

  static TokenFacts parseFacts(String body) throws Exception {
    JsonNode n = JSON.readTree(body);
    JsonNode fees = n.path("custom_fees");
    int customFees =
        fees.path("fixed_fees").size() + fees.path("fractional_fees").size() + fees.path("royalty_fees").size();
    return new TokenFacts(
        n.path("token_id").asText(),
        n.path("name").asText(null),
        n.path("symbol").asText(null),
        n.path("decimals").asInt(0),
        n.path("type").asText(null),
        parseLong(n.path("total_supply")),
        parseLong(n.path("initial_supply")),
        parseLong(n.path("max_supply")),
        n.path("supply_type").asText(null),
        n.path("treasury_account_id").asText(null),
        n.path("memo").asText(""),
        n.path("created_timestamp").asText(null),
        n.path("deleted").asBoolean(false),
        n.path("pause_status").asText(null),
        n.path("freeze_default").asBoolean(false),
        customFees,
        new TokenKeys(
            key(n.path("admin_key")),
            key(n.path("supply_key")),
            key(n.path("freeze_key")),
            key(n.path("wipe_key")),
            key(n.path("kyc_key")),
            key(n.path("pause_key")),
            key(n.path("fee_schedule_key"))));
  }

  static List<Holder> parseHolders(String body) throws Exception {
    List<Holder> holders = new ArrayList<>();
    for (JsonNode b : JSON.readTree(body).path("balances")) {
      long balance = b.path("balance").asLong(0);
      if (balance > 0) {
        holders.add(new Holder(b.path("account").asText(), balance));
      }
    }
    return holders;
  }

  static TransactionFacts parseTransaction(String body) throws Exception {
    JsonNode transactions = JSON.readTree(body).path("transactions");
    if (!transactions.isArray() || transactions.isEmpty()) {
      return null;
    }
    JsonNode tx = transactions.get(0);
    for (JsonNode candidate : transactions) {
      if (!candidate.path("scheduled").asBoolean(false)) {
        tx = candidate;
        break;
      }
    }
    List<TokenChange> changes = new ArrayList<>();
    for (JsonNode t : tx.path("token_transfers")) {
      changes.add(new TokenChange(t.path("token_id").asText(), t.path("account").asText(), t.path("amount").asLong()));
    }
    return new TransactionFacts(
        tx.path("result").asText(null),
        tx.path("name").asText(null),
        tx.path("entity_id").isNull() ? null : tx.path("entity_id").asText(null),
        tx.path("charged_tx_fee").asLong(0),
        tx.path("consensus_timestamp").asText(null),
        changes);
  }

  /** The raw public key hex, as {@code {"_type":"ED25519","key":"..."}} shows it; null when absent. */
  private static String key(JsonNode node) {
    return node == null || node.isNull() || node.isMissingNode() ? null : node.path("key").asText(null);
  }

  /** The Mirror Node sends supplies as strings: "1000000". */
  private static Long parseLong(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode() || node.asText().isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(node.asText());
    } catch (NumberFormatException e) {
      return null;
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

  private HttpResponse<String> get(String path) throws Exception {
    String base = properties.getMirrorNodeUrl();
    base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /** Which keys a token has; each is the raw public key hex, null when the token has none. */
  public record TokenKeys(
      String admin, String supply, String freeze, String wipe, String kyc, String pause, String feeSchedule) {}

  /**
   * @param totalSupply in the smallest unit
   * @param maxSupply in the smallest unit; 0 or null for no cap (INFINITE)
   */
  public record TokenFacts(
      String tokenId,
      String name,
      String symbol,
      int decimals,
      String type,
      Long totalSupply,
      Long initialSupply,
      Long maxSupply,
      String supplyType,
      String treasury,
      String memo,
      String createdTimestamp,
      boolean deleted,
      String pauseStatus,
      boolean freezeDefault,
      int customFees,
      TokenKeys keys) {}

  public record FactsLookup(LookupState state, TokenFacts facts) {}

  public record Holder(String account, long balance) {}

  public record HoldersLookup(LookupState state, List<Holder> holders) {}

  public record TokenChange(String tokenId, String account, long amount) {}

  /**
   * @param entityId the entity the transaction created, e.g. the new token id; null otherwise
   * @param chargedFeeTinybars what the network actually charged
   */
  public record TransactionFacts(
      String result,
      String name,
      String entityId,
      long chargedFeeTinybars,
      String consensusTimestamp,
      List<TokenChange> tokenChanges) {}

  public record TransactionLookup(LookupState state, TransactionFacts transaction) {}
}
