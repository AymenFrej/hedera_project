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

  /**
   * Reserves the id the next transfer will use, so it can be stored before anything is sent. After
   * a crash mid-transfer, that id is what lets the Mirror Node tell us whether the transfer
   * happened. Null for the mock, which sends nothing.
   */
  String newTransactionId();

  /** Account payments currently leave from; null for the mock, which has none. */
  String payerAccount();

  PaymentResult transferHbar(String transactionId, String destination, long tinybars, String memo);

  PaymentResult transferToken(
      String transactionId, String tokenId, String destination, long units, String memo);

  /** True when transfers really reach Hedera. */
  boolean isLive();

  /**
   * How a transfer ended, as far as the caller can know.
   *
   * <p>{@code UNKNOWN} matters: a timeout does not mean the transfer failed, it may well have
   * reached consensus. Only the Mirror Node can settle it afterwards.
   */
  enum Outcome {
    SUCCESS,
    FAILED,
    UNKNOWN
  }

  /**
   * @param status receipt status as reported by Hedera, e.g. SUCCESS or INSUFFICIENT_PAYER_BALANCE,
   *     or the error when no receipt came back
   * @param sourceAccount account the funds left from; null for the mock
   */
  record PaymentResult(
      Outcome outcome, String transactionId, String status, String sourceAccount, boolean mock) {}
}
