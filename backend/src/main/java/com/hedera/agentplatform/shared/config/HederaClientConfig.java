package com.hedera.agentplatform.shared.config;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Hedera {@link Client} when operator credentials are configured.
 *
 * <p>No credentials means no client bean, which means the mock gateways stay in place. That keeps
 * the application runnable for teammates who have not created a testnet account.
 */
@Configuration
// Must test for a NON-EMPTY value: @ConditionalOnProperty matches a property that exists even when
// it is blank, and `hedera.operator-id` always exists (it defaults to an empty string).
@ConditionalOnExpression("!'${hedera.operator-id:}'.trim().isEmpty()")
public class HederaClientConfig {

  private static final Logger log = LoggerFactory.getLogger(HederaClientConfig.class);

  @Bean(destroyMethod = "close")
  Client hederaClient(HederaProperties properties) throws Exception {
    if (!properties.hasOperatorCredentials()) {
      throw new IllegalStateException(
          "hedera.operator-id is set but hedera.operator-private-key is missing");
    }

    AccountId operatorId = AccountId.fromString(properties.getOperatorId().trim());
    PrivateKey operatorKey = parsePrivateKey(properties);

    Client client =
        switch (properties.getNetwork().toLowerCase()) {
          case "mainnet" -> Client.forMainnet();
          case "previewnet" -> Client.forPreviewnet();
          default -> Client.forTestnet();
        };

    client.setOperator(operatorId, operatorKey);
    client.setDefaultMaxTransactionFee(new Hbar(2));
    // Testnet nodes answer BUSY fairly often; retry generously instead of failing the request.
    client.setMaxAttempts(10);
    client.setMaxBackoff(Duration.ofSeconds(10));
    client.setRequestTimeout(Duration.ofSeconds(90));

    log.info(
        "Hedera client ready: network={} operator={}",
        properties.getNetwork(),
        properties.getOperatorId());
    return client;
  }

  /**
   * The developer portal shows the key in several encodings. Accept the HEX form with or without
   * the {@code 0x} prefix as well as the DER form, so nobody loses time on "invalid characters
   * encountered in Hex string".
   */
  static PrivateKey parsePrivateKey(HederaProperties properties) {
    String raw = properties.getOperatorPrivateKey().trim();
    if (raw.startsWith("0x") || raw.startsWith("0X")) {
      raw = raw.substring(2);
    }
    return "ED25519".equalsIgnoreCase(properties.getKeyType())
        ? PrivateKey.fromStringED25519(raw)
        : PrivateKey.fromStringECDSA(raw);
  }
}
