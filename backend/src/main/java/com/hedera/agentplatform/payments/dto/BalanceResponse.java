package com.hedera.agentplatform.payments.dto;

import java.time.Instant;
import java.util.List;

/**
 * Balances of the account payments leave from, as the Mirror Node reports them. Facts only: no
 * limits, thresholds or policy verdicts belong here.
 *
 * @param available false when there is nothing to report (simulation mode, Mirror Node down)
 * @param detail why {@code available} is false; null otherwise
 * @param account the paying account; null in simulation mode
 * @param asOf consensus time the balances were computed at
 */
public record BalanceResponse(
    boolean available,
    String detail,
    String account,
    Asset hbar,
    List<Asset> tokens,
    Instant asOf,
    String explorerUrl) {

  /**
   * @param units balance in the smallest unit (tinybars, or the token's smallest unit)
   * @param amount the same balance as a decimal, e.g. "999.67250794"
   * @param symbol "HBAR", or the token symbol when known
   */
  public record Asset(
      String tokenId, String symbol, String name, int decimals, long units, String amount) {}
}
