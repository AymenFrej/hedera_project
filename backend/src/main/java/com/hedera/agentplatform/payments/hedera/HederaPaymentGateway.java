package com.hedera.agentplatform.payments.hedera;

/**
 * Moves value on Hedera.
 *
 * <p>The implementation in use depends on configuration: {@link MockHederaPaymentGateway} when no
 * operator credentials are set, {@link SdkHederaPaymentGateway} otherwise.
 *
 * <p>Amounts are always in the smallest unit (tinybars, or the token's smallest unit), so no
 * rounding can happen here.
 */
public interface HederaPaymentGateway {

  PaymentResult transferHbar(String destination, long tinybars, String memo);

  PaymentResult transferToken(String tokenId, String destination, long units, String memo);

  /** True when transfers really reach Hedera. */
  boolean isLive();

  /**
   * @param success true when the network accepted the transfer (receipt status SUCCESS)
   * @param status receipt status as reported by Hedera, e.g. SUCCESS or INSUFFICIENT_PAYER_BALANCE
   * @param sourceAccount account the funds left from; null for the mock
   */
  record PaymentResult(
      boolean success, String transactionId, String status, String sourceAccount, boolean mock) {}
}
