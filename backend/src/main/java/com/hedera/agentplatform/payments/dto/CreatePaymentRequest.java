package com.hedera.agentplatform.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/payments.
 *
 * @param destination Hedera account id, e.g. 0.0.12345
 * @param amount decimal string. HBAR accepts up to 8 decimals (1 tinybar); an HTS token amount is
 *     expressed in the token's smallest unit, so it must be a whole number
 * @param tokenId HTS token id for a token transfer; omit for HBAR
 * @param envelope budget envelope the policy engine checks against (RENT, ESSENTIALS, EMERGENCY)
 * @param memo optional transaction memo, at most 100 bytes on Hedera
 */
public record CreatePaymentRequest(
    @NotBlank @Pattern(regexp = ACCOUNT_ID, message = "must be a Hedera account id like 0.0.12345")
        String destination,
    @NotBlank @Pattern(regexp = "\\d+(\\.\\d+)?", message = "must be a positive decimal number")
        String amount,
    @Pattern(regexp = ACCOUNT_ID, message = "must be a Hedera token id like 0.0.12345")
        String tokenId,
    @Pattern(regexp = "(?i)RENT|ESSENTIALS|EMERGENCY", message = "must be RENT, ESSENTIALS or EMERGENCY")
        String envelope,
    @Size(max = 100) String memo) {

  public static final String ACCOUNT_ID = "\\d+\\.\\d+\\.\\d+";
}
