package com.hedera.agentplatform.payments.mirror;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorTransaction;
import org.junit.jupiter.api.Test;

class PaymentMirrorClientTest {

  /** Trimmed from the real response for the module's first testnet transfer. */
  private static final String HBAR_TRANSFER =
      """
      {"transactions":[{"consensus_timestamp":"1790194854.872086514","name":"CRYPTOTRANSFER",
        "result":"SUCCESS","scheduled":false,
        "transaction_id":"0.0.5239440-1790194819-526000707",
        "transfers":[{"account":"0.0.802","amount":128158},
                     {"account":"0.0.5239440","amount":-1128158},
                     {"account":"0.0.10682427","amount":1000000}],
        "token_transfers":[]}]}
      """;

  @Test
  void converts_sdk_transaction_ids_to_mirror_ids() {
    assertThat(PaymentMirrorClient.toMirrorId("0.0.5239440@1790194819.526000707"))
        .isEqualTo("0.0.5239440-1790194819-526000707");
  }

  @Test
  void parses_hbar_transfers_including_the_fee() throws Exception {
    MirrorTransaction tx = PaymentMirrorClient.parse(HBAR_TRANSFER);

    assertThat(tx.result()).isEqualTo("SUCCESS");
    assertThat(tx.consensusTimestamp()).isEqualTo("1790194854.872086514");
    assertThat(tx.hbarTransfers())
        .containsEntry("0.0.10682427", 1_000_000L)
        .containsEntry("0.0.5239440", -1_128_158L);
  }

  @Test
  void parses_token_transfers() throws Exception {
    MirrorTransaction tx =
        PaymentMirrorClient.parse(
            """
            {"transactions":[{"result":"SUCCESS","scheduled":false,"transfers":[],
              "token_transfers":[{"token_id":"0.0.7777","account":"0.0.1","amount":-500},
                                 {"token_id":"0.0.7777","account":"0.0.2","amount":500}]}]}
            """);

    assertThat(tx.tokenChange("0.0.7777", "0.0.2")).isEqualTo(500);
    assertThat(tx.tokenChange("0.0.7777", "0.0.1")).isEqualTo(-500);
    assertThat(tx.tokenChange("0.0.9999", "0.0.2")).isZero();
  }

  @Test
  void prefers_the_non_scheduled_entry() throws Exception {
    MirrorTransaction tx =
        PaymentMirrorClient.parse(
            """
            {"transactions":[{"result":"FAIL_INVALID","scheduled":true,"transfers":[]},
                             {"result":"SUCCESS","scheduled":false,"transfers":[]}]}
            """);
    assertThat(tx.result()).isEqualTo("SUCCESS");
  }

  @Test
  void an_empty_response_holds_no_transaction() throws Exception {
    assertThat(PaymentMirrorClient.parse("{\"transactions\":[]}")).isNull();
  }
}
