package com.hedera.agentplatform.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.HederaTestFixtures.CreatedAccount;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.hedera.WalletKeys;
import com.hedera.agentplatform.payments.hedera.WalletKeys.UserWallet;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.agentplatform.payments.service.PaymentService;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.ActorType;
import com.hedera.agentplatform.shared.security.ActorResolver;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Paying from a user's own wallet, on the real testnet. A user account is created with 2 HBAR; the
 * "signed-in" user is that account (standing in for the Accounts module's session and decrypted
 * key). The Mirror Node must then show the funds and the fee leaving the user's account.
 *
 * <pre>
 *   ./mvnw test -Dtest=PaymentUserWalletLiveIT
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentUserWalletLiveIT {

  private static final long TENTH_OF_HBAR = 10_000_000L;

  @Autowired private PaymentService payments;
  @Autowired private Client client;
  @MockitoBean private WalletKeys walletKeys;
  @MockitoBean private ActorResolver actors;

  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  private PaymentPolicy policy;

  private CreatedAccount user;
  private CreatedAccount recipient;

  @BeforeAll
  void createUserAndRecipient() throws Exception {
    user = HederaTestFixtures.createAccount(client, new Hbar(2));
    recipient = HederaTestFixtures.createAccount(client, new Hbar(0));
    System.out.printf("LIVE_USER=%s RECIPIENT=%s%n", user.id(), recipient.id());
  }

  @BeforeEach
  void signedInAsTheUser() {
    when(policy.evaluate(any()))
        .thenReturn(new PaymentPolicyDecision(Verdict.ALLOW, "test.allow", "live wallet test"));
    signIn(Actor.user("user_live", user.id().toString()));
    when(walletKeys.walletOf(any()))
        .thenAnswer(
            call -> {
              Actor actor = call.getArgument(0);
              return actor.type() == ActorType.USER
                  ? Optional.of(new UserWallet(user.id().toString(), user.key()))
                  : Optional.empty();
            });
  }

  private void signIn(Actor actor) {
    when(actors.currentActor()).thenReturn(actor);
  }

  private PaymentResponse payTenthOfHbar() {
    return payments.create(
        new CreatePaymentRequest(recipient.id().toString(), "0.1", null, null, "user wallet test"));
  }

  @Test
  void a_signed_in_user_pays_from_their_own_wallet_and_pays_the_fee() throws Exception {
    PaymentResponse p = payTenthOfHbar();

    assertThat(p.status()).as(p.failureReason()).isEqualTo("CONFIRMED");
    assertThat(p.sourceAccount()).isEqualTo(user.id().toString());
    assertThat(p.transactionId()).as("fee payer is the user").startsWith(user.id() + "@");
    assertThat(p.requestedByType()).isEqualTo("USER");

    PaymentVerification v = null;
    for (int attempt = 0; attempt < 15 && (v == null || v.ledgerResult() == null); attempt++) {
      Thread.sleep(2000);
      v = payments.verify(p.id());
    }
    assertThat(v.verified()).as(v.detail()).isTrue();
    assertThat(v.checks())
        .filteredOn(c -> c.name().equals("Sender paid"))
        .singleElement()
        .satisfies(c -> assertThat(c.expected()).contains(user.id().toString()));
    System.out.println("LIVE_EXPLORER=" + p.explorerUrl());
  }

  @Test
  void without_a_wallet_the_platform_operator_pays_as_before() {
    signIn(Actor.system("platform"));

    PaymentResponse p = payTenthOfHbar();

    assertThat(p.status()).as(p.failureReason()).isEqualTo("CONFIRMED");
    assertThat(p.sourceAccount()).isEqualTo(client.getOperatorAccountId().toString());
  }
}
