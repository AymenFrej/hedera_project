package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.dto.BalanceResponse;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PaymentBalanceTest {

  @Autowired private PaymentService service;
  @MockitoBean private PaymentMirrorClient mirror;
  @MockitoBean private HederaPaymentGateway gateway;

  @Test
  void reports_hbar_and_tokens_as_decimals_and_smallest_units() {
    when(gateway.payerAccount()).thenReturn("0.0.5239440");
    when(mirror.findBalances(anyString()))
        .thenReturn(
            new BalanceLookup(
                LookupState.FOUND,
                new AccountBalances(
                    "0.0.5239440",
                    99_967_250_794L,
                    "1790195836.839746104",
                    List.of(new TokenHolding("0.0.7777", "USDC", "USD Coin", 6, 5_000_000L)))));

    BalanceResponse b = service.balance();

    assertThat(b.available()).isTrue();
    assertThat(b.account()).isEqualTo("0.0.5239440");
    assertThat(b.hbar().amount()).isEqualTo("999.67250794");
    assertThat(b.hbar().units()).isEqualTo(99_967_250_794L);
    assertThat(b.tokens()).singleElement().satisfies(t -> {
      assertThat(t.symbol()).isEqualTo("USDC");
      assertThat(t.amount()).isEqualTo("5.000000");
    });
    assertThat(b.asOf()).isEqualTo(Instant.ofEpochSecond(1790195836L, 839746104L));
  }

  @Test
  void simulation_mode_has_no_balance_to_report() {
    when(gateway.payerAccount()).thenReturn(null);

    BalanceResponse b = service.balance();

    assertThat(b.available()).isFalse();
    assertThat(b.detail()).contains("Simulation mode");
    assertThat(b.hbar()).isNull();
  }

  @Test
  void says_so_when_the_mirror_node_is_down() {
    when(gateway.payerAccount()).thenReturn("0.0.5239440");
    when(mirror.findBalances(anyString()))
        .thenReturn(new BalanceLookup(LookupState.UNAVAILABLE, null));

    BalanceResponse b = service.balance();

    assertThat(b.available()).isFalse();
    assertThat(b.detail()).contains("could not be reached");
  }
}
