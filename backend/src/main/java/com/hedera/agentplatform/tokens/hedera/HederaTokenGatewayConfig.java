package com.hedera.agentplatform.tokens.hedera;

import com.hedera.hashgraph.sdk.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The SDK gateway when a Hedera client exists, the simulated one otherwise, like Payments. */
@Configuration
public class HederaTokenGatewayConfig {

  private static final Logger log = LoggerFactory.getLogger(HederaTokenGatewayConfig.class);

  @Bean
  HederaTokenGateway hederaTokenGateway(ObjectProvider<Client> clientProvider) {
    Client client = clientProvider.getIfAvailable();
    if (client == null) {
      log.warn("No Hedera operator credentials: token operations are SIMULATED, nothing is created.");
      return new SimulatedHederaTokenGateway();
    }
    log.info("Tokens are created with the platform operator {} as treasury.", client.getOperatorAccountId());
    return new SdkHederaTokenGateway(client);
  }
}
