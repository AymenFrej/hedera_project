package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The amount sent must be exactly the amount requested: refuse anything that would round. */
class PaymentAmountTest {

  private static long units(String amount, int decimals) {
    return PaymentService.toUnits(new BigDecimal(amount), decimals, "HBAR");
  }

  @Test
  void hbar_is_converted_to_tinybars() {
    assertThat(units("1", 8)).isEqualTo(100_000_000L);
    assertThat(units("0.00000001", 8)).isEqualTo(1L);
    assertThat(units("12.5", 8)).isEqualTo(1_250_000_000L);
  }

  @Test
  void hbar_below_one_tinybar_is_refused() {
    assertThatThrownBy(() -> units("0.000000001", 8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("8 decimals");
  }

  @Test
  void token_amounts_use_the_token_decimals() {
    // USDC-like, 6 decimals: 1.5 is 1,500,000 smallest units.
    assertThat(PaymentService.toUnits(new BigDecimal("1.5"), 6, "0.0.1")).isEqualTo(1_500_000L);
    // PAYTEST, 0 decimals: whole units only.
    assertThat(PaymentService.toUnits(new BigDecimal("500"), 0, "0.0.1")).isEqualTo(500L);
    assertThatThrownBy(() -> PaymentService.toUnits(new BigDecimal("1.5"), 0, "0.0.1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Token 0.0.1 supports at most 0 decimals");
  }

  @Test
  void zero_is_refused() {
    assertThatThrownBy(() -> units("0", 8)).isInstanceOf(IllegalArgumentException.class);
  }
}
