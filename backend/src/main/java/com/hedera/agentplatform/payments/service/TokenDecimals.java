package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenLookup;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * How many decimals an asset has: 8 for HBAR, the token's own for an HTS token, read once from the
 * Mirror Node. A token's decimals are fixed at creation, so they are cached for good.
 */
@Component
class TokenDecimals {

  static final int HBAR = 8;

  private final PaymentMirrorClient mirror;
  private final HederaProperties properties;
  private final ConcurrentHashMap<String, Integer> known = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> symbols = new ConcurrentHashMap<>();

  TokenDecimals(PaymentMirrorClient mirror, HederaProperties properties) {
    this.mirror = mirror;
    this.properties = properties;
  }

  /**
   * How to write the asset for a person: "HBAR", or the token's symbol. Falls back to the token id
   * when the symbol cannot be read; never fails.
   */
  String symbol(String tokenId) {
    if (tokenId == null) {
      return "HBAR";
    }
    String cached = symbols.get(tokenId);
    if (cached != null) {
      return cached;
    }
    try {
      of(tokenId);
    } catch (RuntimeException e) {
      return tokenId;
    }
    return symbols.getOrDefault(tokenId, tokenId);
  }

  /**
   * @throws IllegalArgumentException when the token does not exist
   * @throws MirrorNodeUnavailableException when the Mirror Node cannot be asked
   */
  int of(String tokenId) {
    if (tokenId == null) {
      return HBAR;
    }
    Integer cached = known.get(tokenId);
    if (cached != null) {
      return cached;
    }
    TokenLookup lookup = mirror.findToken(tokenId);
    return switch (lookup.state()) {
      case FOUND -> {
        known.put(tokenId, lookup.token().decimals());
        if (lookup.token().symbol() != null) {
          symbols.put(tokenId, lookup.token().symbol());
        }
        yield lookup.token().decimals();
      }
      case NOT_FOUND ->
          throw new IllegalArgumentException(
              "No token " + tokenId + " on " + properties.getNetwork());
      case UNAVAILABLE ->
          throw new MirrorNodeUnavailableException(
              "Mirror Node could not be reached to read the decimals of " + tokenId);
    };
  }
}
