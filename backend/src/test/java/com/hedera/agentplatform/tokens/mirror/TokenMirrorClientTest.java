package com.hedera.agentplatform.tokens.mirror;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenFacts;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TransactionFacts;
import org.junit.jupiter.api.Test;

/** Parsing of real Mirror Node answer shapes (testnet, abridged). */
class TokenMirrorClientTest {

  @Test
  void token_facts_keep_supplies_exact_and_keys_as_raw_hex() throws Exception {
    TokenFacts f = TokenMirrorClient.parseFacts("""
        {"token_id":"0.0.10687138","name":"Payment test","symbol":"PAYTEST","decimals":"0",
         "type":"FUNGIBLE_COMMON","total_supply":"1000000","initial_supply":"1000000","max_supply":"0",
         "supply_type":"INFINITE","treasury_account_id":"0.0.5239440","memo":"",
         "created_timestamp":"1790000000.123456789","deleted":false,"pause_status":"NOT_APPLICABLE",
         "freeze_default":false,
         "admin_key":null,
         "supply_key":{"_type":"ECDSA_SECP256K1","key":"02abcdef"},
         "freeze_key":null,"wipe_key":null,"kyc_key":null,"pause_key":null,"fee_schedule_key":null,
         "custom_fees":{"created_timestamp":"1790000000.123456789","fixed_fees":[],"fractional_fees":[]}}
        """);

    assertThat(f.symbol()).isEqualTo("PAYTEST");
    assertThat(f.totalSupply()).isEqualTo(1_000_000L);
    assertThat(f.maxSupply()).isZero();
    assertThat(f.supplyType()).isEqualTo("INFINITE");
    assertThat(f.keys().admin()).isNull();
    assertThat(f.keys().supply()).isEqualTo("02abcdef");
    assertThat(f.customFees()).isZero();
  }

  @Test
  void holders_leave_out_empty_balances() throws Exception {
    assertThat(TokenMirrorClient.parseHolders("""
        {"timestamp":"1790000000.0","balances":[
          {"account":"0.0.5239440","balance":999685,"decimals":0},
          {"account":"0.0.10687139","balance":315,"decimals":0},
          {"account":"0.0.42","balance":0,"decimals":0}]}
        """)).extracting(TokenMirrorClient.Holder::account).containsExactly("0.0.5239440", "0.0.10687139");
  }

  @Test
  void a_transaction_gives_the_created_entity_and_the_fee_really_charged() throws Exception {
    TransactionFacts t = TokenMirrorClient.parseTransaction("""
        {"transactions":[{"result":"SUCCESS","name":"TOKENCREATION","entity_id":"0.0.10700001",
          "charged_tx_fee":1123456789,"consensus_timestamp":"1790000001.000000001","scheduled":false,
          "token_transfers":[{"token_id":"0.0.10700001","account":"0.0.5239440","amount":1000000}]}]}
        """);

    assertThat(t.entityId()).isEqualTo("0.0.10700001");
    assertThat(t.chargedFeeTinybars()).isEqualTo(1_123_456_789L);
    assertThat(t.tokenChanges()).singleElement().satisfies(c -> assertThat(c.amount()).isEqualTo(1_000_000L));
  }

  @Test
  void transaction_ids_use_the_mirror_node_form() {
    assertThat(TokenMirrorClient.toMirrorId("0.0.5239440@1790000000.000000001"))
        .isEqualTo("0.0.5239440-1790000000-000000001");
  }
}
