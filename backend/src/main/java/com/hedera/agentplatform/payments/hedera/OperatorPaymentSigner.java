package com.hedera.agentplatform.payments.hedera;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TransferTransaction;

/**
 * Default until Accounts provides user wallets: funds leave from the platform operator, which the
 * client already signs for.
 */
public class OperatorPaymentSigner implements PaymentSigner {

  @Override
  public AccountId payer(Client client) {
    return client.getOperatorAccountId();
  }

  @Override
  public TransferTransaction prepare(TransferTransaction transaction, Client client) {
    return transaction;
  }
}
