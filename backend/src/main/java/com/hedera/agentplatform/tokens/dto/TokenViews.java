package com.hedera.agentplatform.tokens.dto;

import java.util.List;

/** What the Tokens API answers. Amounts are shown in whole tokens, as a person reads them. */
public final class TokenViews {

  private TokenViews() {}

  /**
   * One thing a token can never do (GUARANTEE) or that someone can do with it (POWER), derived
   * from its keys: on Hedera a key that is absent cannot be added unless the token has an admin key.
   */
  public record Promise(String kind, String title, String detail) {}

  public record TokenPreview(
      boolean valid,
      List<String> problems,
      List<String> warnings,
      String name,
      String symbol,
      int decimals,
      String initialSupply,
      String maxSupply,
      String supplyType,
      boolean mintable,
      String memo,
      String treasury,
      boolean live,
      List<Promise> promises,
      List<String> steps) {}

  public record MintPreview(
      boolean valid,
      List<String> problems,
      String tokenId,
      String symbol,
      int decimals,
      String amount,
      String supplyBefore,
      String supplyAfter,
      String maxSupply,
      boolean live) {}

  public record Operation(
      String id,
      String kind,
      String status,
      String tokenId,
      String name,
      String symbol,
      Integer decimals,
      String amount,
      String maxSupply,
      Boolean mintable,
      String memo,
      String treasury,
      String transactionId,
      String networkStatus,
      String totalSupplyAfter,
      String failureReason,
      String feeHbar,
      String consensusTimestamp,
      String requestedById,
      String createdAt,
      String transactionUrl,
      String tokenUrl) {}

  public record TokenCard(
      String tokenId, String symbol, String name, int decimals, String balance, boolean createdHere) {}

  public record Portfolio(
      boolean live, String treasury, String asOf, boolean mirrorAvailable, List<TokenCard> tokens) {}

  /** @param share of the total supply, 0 to 1 */
  public record HolderView(String account, String balance, double share, boolean treasury) {}

  public record Passport(
      String tokenId,
      String name,
      String symbol,
      int decimals,
      String type,
      String totalSupply,
      String maxSupply,
      String supplyType,
      String treasury,
      String memo,
      String createdAt,
      String pauseStatus,
      List<Promise> promises,
      List<HolderView> holders,
      boolean holdersComplete,
      double treasuryShare,
      boolean canMint,
      String mintReason,
      List<Operation> operations,
      String tokenUrl) {}

  /**
   * @param state CAN_RECEIVE, AUTO_ASSOCIATES, NEEDS_ASSOCIATION, NO_ACCOUNT, DELETED
   */
  public record Receivability(String accountId, String tokenId, String state, boolean canReceive, String detail) {}

  public record Check(String label, boolean passed, String detail) {}

  /**
   * @param state VERIFIED, MISMATCH, PENDING (not on the Mirror Node yet), NOT_SUBMITTED, UNAVAILABLE
   */
  public record Verification(
      String state,
      String summary,
      List<Check> checks,
      String feeHbar,
      String consensusTimestamp,
      String transactionUrl) {}
}
