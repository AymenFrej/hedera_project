package com.hedera.agentplatform.payments.hedera;

import com.hedera.hashgraph.sdk.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the payment gateway at startup: the SDK one when a Hedera client is available, the mock
 * otherwise. Same approach as the audit module's {@code HederaAuditGatewayConfig}.
 */
@Configuration
public class HederaPaymentGatewayConfig {

  private static final Logger log = LoggerFactory.getLogger(HederaPaymentGatewayConfig.class);

  @Bean
  HederaPaymentGateway hederaPaymentGateway(
      ObjectProvider<Client> clientProvider, ObjectProvider<PaymentSigner> signerProvider) {
    Client client = clientProvider.getIfAvailable();
    if (client == null) {
      log.warn(
          "No Hedera operator credentials: payments are SIMULATED, nothing is transferred. "
              + "Set HEDERA_OPERATOR_ID and HEDERA_OPERATOR_PRIVATE_KEY to send real transfers.");
      return new MockHederaPaymentGateway();
    }

    PaymentSigner signer = signerProvider.getIfAvailable();
    if (signer == null) {
      log.warn(
          "No PaymentSigner bean: payments leave from the platform operator {}, not from user "
              + "wallets. The Accounts module can replace this by declaring a PaymentSigner bean.",
          client.getOperatorAccountId());
      signer = new OperatorPaymentSigner();
    }
    return new SdkHederaPaymentGateway(client, signer);
  }
}
