package com.hedera.agentplatform.tokens.hedera;

/**
 * Without Hedera credentials: every operation is answered SIMULATED, with no token id and no
 * transaction id, so the page can never present a simulated token as one that exists.
 */
public class SimulatedHederaTokenGateway implements HederaTokenGateway {

  @Override
  public boolean isLive() {
    return false;
  }

  @Override
  public String treasuryAccount() {
    return null;
  }

  @Override
  public String supplyPublicKey() {
    return null;
  }

  @Override
  public String newTransactionId() {
    return null;
  }

  @Override
  public TokenResult create(String transactionId, TokenPlan plan) {
    return new TokenResult(Outcome.SIMULATED, null, "SIMULATED", null, null);
  }

  @Override
  public TokenResult mint(String transactionId, String tokenId, long units) {
    return new TokenResult(Outcome.SIMULATED, null, "SIMULATED", tokenId, null);
  }
}
