package com.hedera.agentplatform.tokens.hedera;

/**
 * The only way the Tokens module reaches Hedera. The SDK implementation creates and mints real
 * tokens with the platform operator as treasury; without credentials a simulated one answers and
 * says so, so nothing is ever presented as on-chain when it is not.
 */
public interface HederaTokenGateway {

  /** true when operations really reach Hedera. */
  boolean isLive();

  /** The account that receives the supply and pays the fees; null when not live. */
  String treasuryAccount();

  /**
   * The public key (raw hex, as the Mirror Node shows it) this platform signs mints with; null when
   * not live. A token can be minted here only when its supply key is this key.
   */
  String supplyPublicKey();

  /** Generated before sending, so the operation can be committed and found again on the ledger. */
  String newTransactionId();

  TokenResult create(String transactionId, TokenPlan plan);

  TokenResult mint(String transactionId, String tokenId, long units);

  /**
   * What to create, already validated: amounts in the smallest unit.
   *
   * @param maxSupplyUnits null for no cap (INFINITE supply type)
   * @param mintable whether the platform keeps a supply key; without one the supply is fixed forever
   */
  record TokenPlan(
      String name,
      String symbol,
      int decimals,
      long initialSupplyUnits,
      Long maxSupplyUnits,
      boolean mintable,
      String memo) {}

  enum Outcome {
    SUCCESS,
    /** The network refused it: nothing changed on the ledger. */
    FAILED,
    /** No receipt (timeout, connection loss): the ledger decides. */
    UNKNOWN,
    /** Not live: nothing was sent. */
    SIMULATED
  }

  /**
   * @param networkStatus Hedera's status, e.g. SUCCESS or TOKEN_HAS_NO_SUPPLY_KEY
   * @param tokenId the created token (create), or the token minted
   * @param totalSupply the total supply after a mint, from the receipt; null otherwise
   */
  record TokenResult(
      Outcome outcome, String transactionId, String networkStatus, String tokenId, Long totalSupply) {}
}
