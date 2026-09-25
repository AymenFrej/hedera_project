package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import com.hedera.agentplatform.accounts.service.AccountKeyProtector;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.Outcome;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.hedera.PayingActor;
import com.hedera.agentplatform.payments.hedera.WalletKeys;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.ActorType;
import com.hedera.agentplatform.shared.security.ActorResolver;
import com.hedera.hashgraph.sdk.PrivateKey;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments from each person's own wallet. Hedera is a stand-in that records whose wallet every
 * transfer is signed for; the wallets, keys, policy and approvals are the real ones.
 */
@SpringBootTest
@Transactional
class UserWalletPaymentsTest {

  private static final String ALICE_WALLET = "0.0.700001";
  private static final String BOB_WALLET = "0.0.700002";
  private static final String TREASURY = "0.0.5239440";

  @Autowired private PaymentService payments;
  @Autowired private WalletKeys wallets;
  @Autowired private PayingActor payingActor;
  @Autowired private UserRepository users;
  @Autowired private AccountRepository accounts;
  @Autowired private AccountKeyProtector keys;
  @Autowired private EnvelopeLedger ledger;
  @MockitoBean private HederaPaymentGateway gateway;
  @MockitoBean private ActorResolver actors;

  private final List<String> signedFor = new ArrayList<>();
  private PrivateKey aliceKey;

  private String walletOfPayer() {
    return wallets.accountOf(payingActor.current()).orElse(TREASURY);
  }

  @BeforeEach
  void setUp() {
    aliceKey = PrivateKey.generateED25519();
    person("alice", "USER", ALICE_WALLET, aliceKey);
    person("bob", "ADMIN", BOB_WALLET, PrivateKey.generateED25519());
    signIn("alice");
    ledger.reset();

    when(gateway.isLive()).thenReturn(true);
    when(gateway.newTransactionId()).thenAnswer(i -> walletOfPayer() + "@1790000000.000000001");
    when(gateway.payerAccount()).thenAnswer(i -> walletOfPayer());
    when(gateway.transferHbar(anyString(), anyString(), anyLong(), any())).thenAnswer(i -> {
      signedFor.add(walletOfPayer());
      return new PaymentResult(Outcome.SUCCESS, i.getArgument(0), "SUCCESS", walletOfPayer(), false);
    });
  }

  private static String any() {
    return org.mockito.ArgumentMatchers.any();
  }

  private void person(String id, String role, String wallet, PrivateKey key) {
    AccountEntity account = new AccountEntity();
    account.id = "acct_" + id;
    account.userId = id;
    account.email = id + "@example.test";
    account.hederaAccountId = wallet;
    account.encryptedPrivateKey = keys.encrypt(key.toString());
    account.balance = BigDecimal.ZERO;
    account.status = "ACTIVE";
    accounts.save(account);
    UserEntity user = new UserEntity();
    user.id = id;
    user.email = account.email;
    user.displayName = id;
    user.passwordHash = "x";
    user.role = role;
    user.accountId = account.id;
    users.saveAndFlush(user);
  }

  private void signIn(String id) {
    when(actors.currentActor()).thenReturn(Actor.user(id, null));
  }

  // --- The key --------------------------------------------------------------------------------------

  @Test
  void a_wallet_key_comes_back_exactly_and_only_with_the_right_secret() {
    var wallet = wallets.walletOf(Actor.user("alice", null)).orElseThrow();

    assertThat(wallet.accountId()).isEqualTo(ALICE_WALLET);
    assertThat(wallet.key().toString()).isEqualTo(aliceKey.toString());
    assertThat(keys.decrypt(keys.encrypt("secret key"))).isEqualTo("secret key");
    assertThatThrownBy(() -> keys.decrypt(java.util.Base64.getEncoder().encodeToString(new byte[40])))
        .hasMessageContaining("another secret");
  }

  @Test
  void the_platform_has_no_wallet_and_a_person_without_one_is_refused() {
    assertThat(wallets.walletOf(Actor.system("platform"))).isEmpty();
    UserEntity ghost = new UserEntity();
    ghost.id = "ghost";
    ghost.email = "ghost@example.test";
    ghost.displayName = "ghost";
    ghost.passwordHash = "x";
    ghost.role = "USER";
    users.saveAndFlush(ghost);

    assertThatThrownBy(() -> wallets.walletOf(Actor.user("ghost", null)))
        .hasMessageContaining("no Hedera wallet");
  }

  // --- Whose wallet pays ----------------------------------------------------------------------------

  @Test
  void a_payment_leaves_from_the_requesters_own_wallet() {
    PaymentResponse sent = payments.create(new CreatePaymentRequest(BOB_WALLET, "1", null, "ESSENTIALS", null));
    // First transfer to Bob is held by the policy (counterparty.unknown); nothing is sent yet.
    assertThat(sent.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(signedFor).isEmpty();
  }

  @Test
  void a_held_payment_approved_by_an_admin_still_leaves_from_the_requesters_wallet() {
    PaymentResponse held = payments.create(new CreatePaymentRequest(BOB_WALLET, "1", null, "ESSENTIALS", null));

    signIn("bob"); // the reviewer's request sends it
    PaymentResponse approved = payments.approve(held.id());

    assertThat(approved.status()).isEqualTo("CONFIRMED");
    assertThat(signedFor).as("never the reviewer's wallet").containsExactly(ALICE_WALLET);
    assertThat(approved.sourceAccount()).isEqualTo(ALICE_WALLET);
  }

  // --- Top-ups --------------------------------------------------------------------------------------

  @Test
  void a_top_up_goes_to_the_requesters_own_wallet_from_the_treasury_after_an_admin() {
    long essentialsBefore = ledger.state().balances().values().stream().mapToLong(Long::longValue).sum();

    PaymentResponse asked = payments.requestTopUp("5", "topup-alice-0001");
    assertThat(asked.kind()).isEqualTo("TOP_UP");
    assertThat(asked.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(asked.destination()).as("from her account, never from the request").isEqualTo(ALICE_WALLET);
    assertThat(asked.approvalId()).as("not a Policies-module approval").isNull();
    assertThat(payments.requestTopUp("5", "topup-alice-0001").id()).as("same key, same top-up").isEqualTo(asked.id());

    signIn("bob");
    PaymentResponse sent = payments.approve(asked.id());

    assertThat(sent.status()).isEqualTo("CONFIRMED");
    assertThat(signedFor).as("paid by the treasury").containsExactly(TREASURY);
    long essentialsAfter = ledger.state().balances().values().stream().mapToLong(Long::longValue).sum();
    assertThat(essentialsAfter).as("the envelopes are not the treasury's budget").isEqualTo(essentialsBefore);
  }

  @Test
  void top_ups_are_capped_and_one_waits_at_a_time() {
    assertThatThrownBy(() -> payments.requestTopUp("11", null)).hasMessageContaining("between 0 and 10");
    assertThatThrownBy(() -> payments.requestTopUp("-1", null)).isInstanceOf(IllegalArgumentException.class);
    payments.requestTopUp("2", null);
    assertThatThrownBy(() -> payments.requestTopUp("2", null)).hasMessageContaining("already have a top-up waiting");
  }

  @Test
  void there_is_no_top_up_in_simulation() {
    when(gateway.isLive()).thenReturn(false);

    assertThatThrownBy(() -> payments.requestTopUp("5", null)).hasMessageContaining("simulation");
  }

  @Test
  void a_payment_carries_whose_request_it_was() {
    PaymentResponse sent = payments.create(new CreatePaymentRequest(BOB_WALLET, "1", null, "ESSENTIALS", null));

    assertThat(sent.requestedById()).isEqualTo("alice");
    assertThat(sent.requestedByType()).isEqualTo(ActorType.USER.name());
    assertThat(sent.kind()).isEqualTo("PAYMENT");
  }
}
