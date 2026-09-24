package com.hedera.agentplatform.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.HederaTestFixtures.CreatedAccount;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.service.PaymentService;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Answers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * HTS payments end to end against the real testnet: payment → Hedera → Mirror Node verification.
 *
 * <p>Creates its own token and recipients on every run, so it depends on nothing left over from a
 * previous run. Skipped unless HEDERA_OPERATOR_ID is set. Run it with:
 *
 * <pre>
 *   ./mvnw test -Dtest=PaymentTokenLiveIT
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentTokenLiveIT {

  /** The treasury (the operator) holds the whole supply: 100 PAYIT. */
  private static final long SUPPLY = 100;

  @Autowired private PaymentService service;
  @Autowired private Client client;

  /** Hedera's behaviour is under test here, not the policy's: every payment is allowed. */
  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  private PaymentPolicy policy;

  private String token;
  private String associated;
  private String notAssociated;

  @BeforeAll
  void createTokenAndRecipients() throws Exception {
    when(policy.evaluate(any()))
        .thenReturn(new PaymentPolicyDecision(Verdict.ALLOW, "test.allow", "live Hedera test"));
    assertThat(service.isLedgerActive()).as("credentials are set, live gateway expected").isTrue();

    TokenId tokenId = HederaTestFixtures.createToken(client, "Payments Live IT", "PAYIT", SUPPLY);
    CreatedAccount a = HederaTestFixtures.createAccount(client, new Hbar(1));
    CreatedAccount b = HederaTestFixtures.createAccount(client, new Hbar(1));
    // Only A is associated: B is the non-associated case, on purpose.
    HederaTestFixtures.associate(client, a, tokenId);

    token = tokenId.toString();
    associated = a.id().toString();
    notAssociated = b.id().toString();
    System.out.printf("LIVE_TOKEN=%s ASSOCIATED=%s NOT_ASSOCIATED=%s%n", token, associated, notAssociated);
  }

  private PaymentResponse pay(String destination, String tokenId, long amount) {
    return service.create(
        new CreatePaymentRequest(destination, String.valueOf(amount), tokenId, null, "PaymentTokenLiveIT"));
  }

  /** Mirror nodes trail consensus by a few seconds; retry briefly rather than sleeping once. */
  private PaymentVerification verifyEventually(String paymentId) throws InterruptedException {
    PaymentVerification v = null;
    for (int attempt = 0; attempt < 15; attempt++) {
      v = service.verify(paymentId);
      if (v.ledgerResult() != null) {
        return v;
      }
      Thread.sleep(2000);
    }
    return v;
  }

  @Test
  @Order(1)
  void a_token_payment_to_an_associated_account_is_confirmed_and_verified() throws Exception {
    PaymentResponse p = pay(associated, token, 10);

    assertThat(p.status()).as(p.failureReason()).isEqualTo("CONFIRMED");
    PaymentVerification v = verifyEventually(p.id());
    assertThat(v.verified()).as(v.detail()).isTrue();
    assertThat(v.checks()).extracting("name").contains("Recipient received", "Sender sent");
    System.out.println("LIVE_EXPLORER=" + p.explorerUrl());
  }

  @Test
  @Order(2)
  void a_token_payment_to_a_non_associated_account_fails() throws Exception {
    PaymentResponse p = pay(notAssociated, token, 5);

    assertThat(p.status()).isEqualTo("FAILED");
    assertThat(p.failureReason()).isEqualTo("TOKEN_NOT_ASSOCIATED_TO_ACCOUNT");
    PaymentVerification v = verifyEventually(p.id());
    assertThat(v.verified()).as("ledger agrees it failed: " + v.detail()).isTrue();
  }

  @Test
  @Order(3)
  void sending_more_tokens_than_held_fails() throws Exception {
    // 90 PAYIT left after the first test; ask for 150.
    PaymentResponse p = pay(associated, token, 150);

    assertThat(p.status()).isEqualTo("FAILED");
    assertThat(p.failureReason()).isEqualTo("INSUFFICIENT_TOKEN_BALANCE");
    PaymentVerification v = verifyEventually(p.id());
    assertThat(v.verified()).as("ledger agrees it failed: " + v.detail()).isTrue();
  }

  @Test
  @Order(4)
  void an_invalid_token_or_recipient_fails_without_moving_anything() {
    // A token that does not exist has no decimals to read: refused before anything is sent.
    assertThatThrownBy(() -> pay(associated, "0.0.999999999", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No token 0.0.999999999");

    PaymentResponse badRecipient = pay("0.0.999999999", token, 1);
    assertThat(badRecipient.status()).isEqualTo("FAILED");
    System.out.println("LIVE_INVALID recipient=" + badRecipient.failureReason());
  }
}
