package com.hedera.agentplatform.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/payments.
 *
 * @param destination Hedera account id, e.g. 0.0.12345
 * @param amount decimal string, as a person types it; HBAR accepts up to 8 decimals, a token up to
 *     its own decimals
 * @param tokenId HTS token id for a token transfer; omit for HBAR
 * @param envelope budget envelope the policy checks against (RENT, ESSENTIALS, EMERGENCY)
 * @param memo optional transaction memo, at most 100 bytes on Hedera
 * @param keepAtLeast the requester's own condition: refuse the payment if less than this would
 *     remain of the asset on the paying account; optional
 */
public record CreatePaymentRequest(
    @NotBlank @Pattern(regexp = ACCOUNT_ID, message = "must be a Hedera account id like 0.0.12345")
        String destination,
    @NotBlank @Pattern(regexp = DECIMAL, message = "must be a positive decimal number")
        String amount,
    @Pattern(regexp = ACCOUNT_ID, message = "must be a Hedera token id like 0.0.12345")
        String tokenId,
    @Pattern(regexp = "(?i)RENT|ESSENTIALS|EMERGENCY", message = "must be RENT, ESSENTIALS or EMERGENCY")
        String envelope,
    @Size(max = 100) String memo,
    @Pattern(regexp = DECIMAL, message = "must be a positive decimal number") String keepAtLeast) {

  public static final String ACCOUNT_ID = "\\d+\\.\\d+\\.\\d+";
  public static final String DECIMAL = "\\d+(\\.\\d+)?";

  /** Without a condition. */
  public CreatePaymentRequest(
      String destination, String amount, String tokenId, String envelope, String memo) {
    this(destination, amount, tokenId, envelope, memo, null);
  }
}
