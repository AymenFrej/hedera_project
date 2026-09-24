package com.hedera.agentplatform.payments;

import com.hedera.agentplatform.payments.HederaTestFixtures.CreatedAccount;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * One-time creation of the permanent demo token and a recipient already associated with it.
 *
 * <p>Not a test: its name matches no test pattern, so {@code mvn test} never runs it. Run it once
 * with credentials, then copy the printed ids into .env:
 *
 * <pre>
 *   ./mvnw test -Dtest=PaymentDemoSetup
 * </pre>
 *
 * <p>Refuses to run when PAYMENT_DEMO_TOKEN_ID is already set, so a second run cannot create a
 * duplicate token by accident. The recipient's key is not printed or kept: after the association,
 * nothing in the demo needs it to sign.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_OPERATOR_ID", matches = ".+")
@DisabledIfEnvironmentVariable(named = "PAYMENT_DEMO_TOKEN_ID", matches = ".+")
class PaymentDemoSetup {

  @Autowired private Client client;

  @Test
  void create_demo_token_and_recipient() throws Exception {
    TokenId token = HederaTestFixtures.createToken(client, "Payments Demo Token", "PAYTEST", 1_000_000);
    CreatedAccount recipient = HederaTestFixtures.createAccount(client, new Hbar(1));
    HederaTestFixtures.associate(client, recipient, token);

    System.out.println("PAYMENT_DEMO_TOKEN_ID=" + token);
    System.out.println("PAYMENT_DEMO_RECIPIENT_ID=" + recipient.id());
  }
}
