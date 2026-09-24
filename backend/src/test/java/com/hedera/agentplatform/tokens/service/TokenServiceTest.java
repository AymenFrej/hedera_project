package com.hedera.agentplatform.tokens.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountInfo;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import com.hedera.agentplatform.tokens.dto.TokenDraft;
import com.hedera.agentplatform.tokens.dto.TokenViews.MintPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Operation;
import com.hedera.agentplatform.tokens.dto.TokenViews.Passport;
import com.hedera.agentplatform.tokens.dto.TokenViews.Promise;
import com.hedera.agentplatform.tokens.dto.TokenViews.TokenPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Verification;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.Outcome;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.TokenPlan;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.TokenResult;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.FactsLookup;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.Holder;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.HoldersLookup;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenChange;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenFacts;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenKeys;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TransactionFacts;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TransactionLookup;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Tokens capability with Hedera and the Mirror Node replaced by stand-ins: what is tested is
 * what the platform validates, promises, sends, records and concludes from the ledger's answers.
 */
@SpringBootTest
@Transactional
class TokenServiceTest {

  private static final String TREASURY = "0.0.5239440";
  private static final String OUR_KEY = "02abcdef";
  private static final String TX = "0.0.5239440@1790000000.000000001";
  private static final AtomicInteger KEYS = new AtomicInteger();

  @Autowired private TokenService tokens;
  @Autowired private AuditEventRepository auditEvents;
  @MockitoBean private HederaTokenGateway gateway;
  @MockitoBean private TokenMirrorClient mirror;
  @MockitoBean private PaymentMirrorClient accounts;

  @BeforeEach
  void live() {
    when(gateway.isLive()).thenReturn(true);
    when(gateway.treasuryAccount()).thenReturn(TREASURY);
    when(gateway.supplyPublicKey()).thenReturn(OUR_KEY);
    when(gateway.newTransactionId()).thenReturn(TX);
    when(accounts.findBalances(TREASURY)).thenReturn(new BalanceLookup(LookupState.FOUND,
        new AccountBalances(TREASURY, 100_000_000L, "1790000000.0", List.of(
            new TokenHolding("0.0.777", "PAYTEST", "Payment test", 0, 999_685L)))));
  }

  private static TokenDraft draft(String supply, String policy, String max) {
    return new TokenDraft("Coffee Beans", "bean", "2", supply, policy, max, "Loyalty points");
  }

  private static String key() {
    return "test-key-" + KEYS.incrementAndGet();
  }

  private static TokenFacts facts(String supplyKey, String supplyType, long total, Long max, String adminKey) {
    return new TokenFacts("0.0.900", "Coffee Beans", "BEAN", 2, "FUNGIBLE_COMMON", total, total, max, supplyType,
        TREASURY, "", "1790000000.000000000", false, "NOT_APPLICABLE", false, 0,
        new TokenKeys(adminKey, supplyKey, null, null, null, null, null));
  }

  // --- Studio -----------------------------------------------------------------------------------

  @Test
  void a_fixed_token_promises_its_supply_can_never_grow() {
    TokenPreview p = tokens.preview(draft("1000000", "FIXED", null));

    assertThat(p.valid()).isTrue();
    assertThat(p.symbol()).isEqualTo("BEAN");
    assertThat(p.supplyType()).isEqualTo("FINITE");
    assertThat(p.maxSupply()).as("fixed = capped at the initial supply, on the ledger itself").isEqualTo("1000000");
    assertThat(p.mintable()).isFalse();
    assertThat(p.promises()).extracting(Promise::title)
        .contains("Nobody can change or delete it", "Supply fixed forever at 1000000 BEAN",
            "Nobody can freeze a holder", "Nobody can take tokens back", "No transfer fees, ever");
    assertThat(p.promises()).allMatch(pr -> pr.kind().equals("GUARANTEE"));
  }

  @Test
  void a_mintable_token_says_who_can_mint_and_up_to_where() {
    TokenPreview capped = tokens.preview(draft("1000", "CAPPED", "5000"));
    TokenPreview unlimited = tokens.preview(draft("1000", "UNLIMITED", null));

    assertThat(capped.promises()).anyMatch(p -> p.kind().equals("POWER")
        && p.title().equals("Never more than 5000 BEAN") && p.detail().contains("This platform will hold the supply key"));
    assertThat(unlimited.supplyType()).isEqualTo("INFINITE");
    assertThat(unlimited.promises()).anyMatch(p -> p.title().equals("More can be minted, with no cap"));
  }

  @Test
  void a_symbol_the_treasury_already_holds_is_flagged() {
    TokenPreview p = tokens.preview(new TokenDraft("Again", "paytest", "0", "10", "FIXED", null, null));

    assertThat(p.warnings()).anyMatch(w -> w.contains("PAYTEST") && w.contains("0.0.777"));
  }

  @Test
  void nothing_is_guessed_in_a_design() {
    TokenPreview p = tokens.preview(new TokenDraft("", "coffee beans!", null, "1.234", null, null, null));

    assertThat(p.valid()).isFalse();
    assertThat(p.problems()).contains("Give the token a name", "The symbol must be 1 to 10 letters or digits, e.g. BEAN",
        "Say how many decimals (0 for whole units only, up to 8)", "Choose a supply: fixed forever, capped or unlimited");
  }

  @Test
  void amounts_are_exact_or_refused() {
    assertThat(TokenService.toUnits("12.5", 2, "x")).isEqualTo(1250);
    assertThat(TokenService.toUnits("1_000_000", 0, "x")).isEqualTo(1_000_000);
    assertThatThrownBy(() -> TokenService.toUnits("1.234", 2, "The amount")).hasMessageContaining("more decimals");
    assertThatThrownBy(() -> TokenService.toUnits("99999999999999999999", 0, "x")).hasMessageContaining("above what Hedera");
    assertThat(tokens.preview(draft("6000", "CAPPED", "5000")).problems())
        .contains("The initial supply cannot be above the cap");
    assertThat(tokens.preview(draft("0", "FIXED", null)).problems())
        .anyMatch(p -> p.startsWith("A fixed supply of zero"));
  }

  // --- Create -----------------------------------------------------------------------------------

  @Test
  void creating_sends_the_plan_once_records_it_and_audits_it() {
    when(gateway.create(eq(TX), any())).thenReturn(new TokenResult(Outcome.SUCCESS, TX, "SUCCESS", "0.0.900", null));
    long auditBefore = auditEvents.count();
    String key = key();

    Operation first = tokens.create(draft("1000000", "FIXED", null), key);
    Operation again = tokens.create(draft("1000000", "FIXED", null), key);

    assertThat(first.status()).isEqualTo("CONFIRMED");
    assertThat(first.tokenId()).isEqualTo("0.0.900");
    assertThat(first.amount()).isEqualTo("1000000");
    assertThat(first.tokenUrl()).isEqualTo("https://hashscan.io/testnet/token/0.0.900");
    assertThat(again.id()).as("same key, same operation: no second token").isEqualTo(first.id());
    ArgumentCaptor<TokenPlan> plan = ArgumentCaptor.forClass(TokenPlan.class);
    verify(gateway, times(1)).create(eq(TX), plan.capture());
    assertThat(plan.getValue().initialSupplyUnits()).as("2 decimals").isEqualTo(100_000_000L);
    assertThat(plan.getValue().maxSupplyUnits()).isEqualTo(100_000_000L);
    assertThat(plan.getValue().mintable()).isFalse();
    assertThat(auditEvents.count()).isEqualTo(auditBefore + 1);
  }

  @Test
  void a_refusal_by_the_network_is_recorded_as_failed() {
    when(gateway.create(any(), any())).thenReturn(new TokenResult(Outcome.FAILED, TX, "INSUFFICIENT_PAYER_BALANCE", null, null));

    Operation op = tokens.create(draft("10", "FIXED", null), key());

    assertThat(op.status()).isEqualTo("FAILED");
    assertThat(op.tokenId()).isNull();
    assertThat(op.failureReason()).contains("INSUFFICIENT_PAYER_BALANCE");
  }

  @Test
  void without_credentials_nothing_is_sent_and_it_says_simulated() {
    when(gateway.isLive()).thenReturn(false);
    when(gateway.newTransactionId()).thenReturn(null);
    when(gateway.create(any(), any())).thenReturn(new TokenResult(Outcome.SIMULATED, null, "SIMULATED", null, null));

    Operation op = tokens.create(draft("10", "FIXED", null), key());

    assertThat(op.status()).isEqualTo("SIMULATED");
    assertThat(op.tokenId()).isNull();
    assertThat(op.transactionUrl()).isNull();
    assertThat(tokens.verify(op.id()).state()).isEqualTo("NOT_SUBMITTED");
  }

  @Test
  void an_operation_without_receipt_is_settled_from_the_ledger_with_its_real_fee() {
    when(gateway.create(any(), any())).thenReturn(new TokenResult(Outcome.UNKNOWN, TX, "TimeoutException", null, null));
    Operation op = tokens.create(draft("1000", "FIXED", null), key());
    assertThat(op.status()).isEqualTo("UNKNOWN");

    when(mirror.transaction(TX)).thenReturn(new TransactionLookup(LookupState.FOUND,
        new TransactionFacts("SUCCESS", "TOKENCREATION", "0.0.901", 123_456_789L, "1790000001.000000000", List.of())));
    when(mirror.facts("0.0.901")).thenReturn(new FactsLookup(LookupState.FOUND,
        new TokenFacts("0.0.901", "Coffee Beans", "BEAN", 2, "FUNGIBLE_COMMON", 100_000L, 100_000L, 100_000L, "FINITE",
            TREASURY, "", "1790000001.0", false, null, false, 0, new TokenKeys(null, null, null, null, null, null, null))));

    Verification v = tokens.verify(op.id());
    Operation settled = tokens.get(op.id());

    assertThat(v.state()).isEqualTo("VERIFIED");
    assertThat(v.feeHbar()).as("what the network really charged").isEqualTo("1.23456789");
    assertThat(settled.status()).isEqualTo("CONFIRMED");
    assertThat(settled.tokenId()).isEqualTo("0.0.901");
    assertThat(settled.feeHbar()).isEqualTo("1.23456789");
  }

  @Test
  void a_ledger_that_disagrees_is_reported_as_a_mismatch() {
    when(gateway.create(any(), any())).thenReturn(new TokenResult(Outcome.SUCCESS, TX, "SUCCESS", "0.0.902", null));
    Operation op = tokens.create(draft("1000", "FIXED", null), key());
    when(mirror.transaction(TX)).thenReturn(new TransactionLookup(LookupState.FOUND,
        new TransactionFacts("SUCCESS", "TOKENCREATION", "0.0.902", 1L, "1790000001.0", List.of())));
    when(mirror.facts("0.0.902")).thenReturn(new FactsLookup(LookupState.FOUND,
        new TokenFacts("0.0.902", "Coffee Beans", "BEAN", 2, "FUNGIBLE_COMMON", 100_000L, 100_000L, 100_000L, "FINITE",
            TREASURY, "", "1790000001.0", false, null, false, 0, new TokenKeys("an-admin-key", null, null, null, null, null, null))));

    Verification v = tokens.verify(op.id());

    assertThat(v.state()).isEqualTo("MISMATCH");
    assertThat(v.checks()).anyMatch(c -> c.label().equals("No admin key") && !c.passed());
  }

  // --- Mint -------------------------------------------------------------------------------------

  @Test
  void a_token_without_supply_key_can_never_be_minted() {
    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts(null, "FINITE", 1000, 1000L, null)));

    MintPreview p = tokens.previewMint("0.0.900", "5");

    assertThat(p.valid()).isFalse();
    assertThat(p.problems()).anyMatch(s -> s.contains("no supply key"));
    assertThatThrownBy(() -> tokens.mint("0.0.900", "5", key())).isInstanceOf(IllegalArgumentException.class);
    verify(gateway, never()).mint(anyString(), anyString(), anyLong());
  }

  @Test
  void only_the_supply_key_holder_mints_and_never_above_the_cap() {
    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts("someone-else", "INFINITE", 1000, null, null)));
    assertThat(tokens.previewMint("0.0.900", "5").problems()).anyMatch(s -> s.contains("not this platform"));

    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts(OUR_KEY, "FINITE", 400_000, 500_000L, null)));
    MintPreview tooMuch = tokens.previewMint("0.0.900", "1000.01");
    assertThat(tooMuch.problems()).anyMatch(s -> s.contains("above its cap of 5000"));
  }

  @Test
  void minting_reports_the_new_supply_from_the_receipt() {
    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts(OUR_KEY, "FINITE", 400_000, 500_000L, null)));
    when(gateway.mint(TX, "0.0.900", 25_000L)).thenReturn(new TokenResult(Outcome.SUCCESS, TX, "SUCCESS", "0.0.900", 425_000L));

    MintPreview p = tokens.previewMint("0.0.900", "250");
    Operation op = tokens.mint("0.0.900", "250", key());

    assertThat(p.supplyBefore()).isEqualTo("4000");
    assertThat(p.supplyAfter()).isEqualTo("4250");
    assertThat(op.status()).isEqualTo("CONFIRMED");
    assertThat(op.totalSupplyAfter()).isEqualTo("4250");

    when(mirror.transaction(TX)).thenReturn(new TransactionLookup(LookupState.FOUND, new TransactionFacts(
        "SUCCESS", "TOKENMINT", null, 5_000L, "1790000002.0", List.of(new TokenChange("0.0.900", TREASURY, 25_000L)))));
    assertThat(tokens.verify(op.id()).state()).isEqualTo("VERIFIED");
  }

  // --- Ledger facts -----------------------------------------------------------------------------

  @Test
  void the_passport_reads_keys_and_holders_from_the_ledger() {
    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts(OUR_KEY, "INFINITE", 10_000, null, "admin")));
    when(mirror.holders("0.0.900", 25)).thenReturn(new HoldersLookup(LookupState.FOUND,
        List.of(new Holder(TREASURY, 75_00), new Holder("0.0.4242", 25_00))));

    Passport p = tokens.passport("0.0.900", List.of());

    assertThat(p.totalSupply()).as("10000 units, 2 decimals").isEqualTo("100");
    assertThat(p.treasuryShare()).isEqualTo(0.75);
    assertThat(p.holders()).hasSize(2);
    assertThat(p.holdersComplete()).isTrue();
    assertThat(p.canMint()).isTrue();
    assertThat(p.promises()).anyMatch(pr -> pr.kind().equals("POWER") && pr.title().equals("Its admin can change or delete it"));
    assertThat(p.promises()).anyMatch(pr -> pr.detail().contains("This platform holds the supply key"));
  }

  @Test
  void an_unknown_token_is_not_found_and_an_unreachable_mirror_is_not_a_guess() {
    when(mirror.facts("0.0.404")).thenReturn(new FactsLookup(LookupState.NOT_FOUND, null));
    when(mirror.facts("0.0.503")).thenReturn(new FactsLookup(LookupState.UNAVAILABLE, null));

    assertThatThrownBy(() -> tokens.passport("0.0.404", List.of())).isInstanceOf(java.util.NoSuchElementException.class);
    assertThatThrownBy(() -> tokens.passport("0.0.503", List.of())).isInstanceOf(TokenMirrorUnavailableException.class);
    assertThatThrownBy(() -> tokens.passport("BEAN", List.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void who_can_receive_a_token_is_read_from_associations_and_auto_slots() {
    when(mirror.facts("0.0.900")).thenReturn(new FactsLookup(LookupState.FOUND, facts(null, "FINITE", 1000, 1000L, null)));
    when(accounts.findAccount("0.0.1")).thenReturn(new AccountLookup(LookupState.FOUND, new AccountInfo(false, 0)));
    when(accounts.findAccount("0.0.2")).thenReturn(new AccountLookup(LookupState.FOUND, new AccountInfo(false, -1)));
    when(accounts.findAccount("0.0.3")).thenReturn(new AccountLookup(LookupState.FOUND, new AccountInfo(false, 0)));
    when(accounts.findAccount("0.0.4")).thenReturn(new AccountLookup(LookupState.NOT_FOUND, null));
    when(accounts.findAssociation(anyString(), eq("0.0.900"))).thenReturn(LookupState.NOT_FOUND);
    when(accounts.findAssociation("0.0.3", "0.0.900")).thenReturn(LookupState.FOUND);

    assertThat(tokens.receivability("0.0.900", "0.0.1").state()).isEqualTo("NEEDS_ASSOCIATION");
    assertThat(tokens.receivability("0.0.900", "0.0.2").state()).isEqualTo("AUTO_ASSOCIATES");
    assertThat(tokens.receivability("0.0.900", "0.0.3").canReceive()).isTrue();
    assertThat(tokens.receivability("0.0.900", "0.0.4").state()).isEqualTo("NO_ACCOUNT");
    assertThat(tokens.receivability("0.0.900", TREASURY).state()).isEqualTo("CAN_RECEIVE");
  }

  @Test
  void the_portfolio_is_what_the_treasury_holds_on_the_ledger() {
    var portfolio = tokens.portfolio();

    assertThat(portfolio.live()).isTrue();
    assertThat(portfolio.tokens()).singleElement().satisfies(t -> {
      assertThat(t.symbol()).isEqualTo("PAYTEST");
      assertThat(t.balance()).isEqualTo("999685");
    });
    verify(mirror, never()).holders(anyString(), anyInt());
  }
}
