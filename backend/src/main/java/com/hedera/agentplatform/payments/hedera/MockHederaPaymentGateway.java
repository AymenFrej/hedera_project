package com.hedera.agentplatform.payments.hedera;

/**
 * Used when no Hedera operator credentials are configured. Nothing is sent: the result carries no
 * transaction id and {@link #isLive()} is false, so the payment ends up SIMULATED, never CONFIRMED.
 */
public class MockHederaPaymentGateway implements HederaPaymentGateway {

  @Override
  public String newTransactionId() {
    return null;
  }

  @Override
  public PaymentResult transferHbar(
      String transactionId, String destination, long tinybars, String memo) {
    return simulated();
  }

  @Override
  public PaymentResult transferToken(
      String transactionId, String tokenId, String destination, long units, String memo) {
    return simulated();
  }

  @Override
  public boolean isLive() {
    return false;
  }

  private static PaymentResult simulated() {
    return new PaymentResult(Outcome.SUCCESS, null, "SIMULATED", null, true);
  }
}
