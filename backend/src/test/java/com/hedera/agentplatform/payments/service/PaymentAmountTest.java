package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The amount sent must be exactly the amount requested: reject anything that would round. */
class PaymentAmountTest {

  @Test
  void hbar_is_converted_to_tinybars() {
    assertThat(PaymentService.toUnits(new BigDecimal("1"), true)).isEqualTo(100_000_000L);
    assertThat(PaymentService.toUnits(new BigDecimal("0.00000001"), true)).isEqualTo(1L);
    assertThat(PaymentService.toUnits(new BigDecimal("12.5"), true)).isEqualTo(1_250_000_000L);
  }

  @Test
  void hbar_below_one_tinybar_is_refused() {
    assertThatThrownBy(() -> PaymentService.toUnits(new BigDecimal("0.000000001"), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("8 decimals");
  }

  @Test
  void token_amounts_must_be_whole_smallest_units() {
    assertThat(PaymentService.toUnits(new BigDecimal("500"), false)).isEqualTo(500L);
    assertThatThrownBy(() -> PaymentService.toUnits(new BigDecimal("1.5"), false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void zero_is_refused() {
    assertThatThrownBy(() -> PaymentService.toUnits(BigDecimal.ZERO, true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
