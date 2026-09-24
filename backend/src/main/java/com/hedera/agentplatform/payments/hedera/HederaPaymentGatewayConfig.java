package com.hedera.agentplatform.payments.hedera;

import com.hedera.agentplatform.shared.security.ActorResolver;
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

  /**
   * Signer, in order of preference: a {@link PaymentSigner} bean if one is declared; the user's own
   * wallet when a {@link WalletKeys} bean exists (the Accounts module); otherwise the operator.
   */
  @Bean
  HederaPaymentGateway hederaPaymentGateway(
      ObjectProvider<Client> clientProvider,
      ObjectProvider<PaymentSigner> signerProvider,
      ObjectProvider<WalletKeys> walletKeysProvider,
      ActorResolver actorResolver) {
    Client client = clientProvider.getIfAvailable();
    if (client == null) {
      log.warn(
          "No Hedera operator credentials: payments are SIMULATED, nothing is transferred. "
              + "Set HEDERA_OPERATOR_ID and HEDERA_OPERATOR_PRIVATE_KEY to send real transfers.");
      return new MockHederaPaymentGateway();
    }

    PaymentSigner signer = signerProvider.getIfAvailable();
    WalletKeys walletKeys = walletKeysProvider.getIfAvailable();
    if (signer == null && walletKeys != null) {
      log.info("Payments leave from the signed-in user's own wallet (custodial keys).");
      signer = new CustodialPaymentSigner(walletKeys, actorResolver);
    }
    if (signer == null) {
      log.warn(
          "No WalletKeys bean: payments leave from the platform operator {}, not from user "
              + "wallets. The Accounts module switches this on by declaring a WalletKeys bean.",
          client.getOperatorAccountId());
      signer = new OperatorPaymentSigner();
    }
    return new SdkHederaPaymentGateway(client, signer);
  }
}
