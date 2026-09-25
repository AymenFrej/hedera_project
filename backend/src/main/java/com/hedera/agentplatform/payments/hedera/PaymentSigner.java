package com.hedera.agentplatform.payments.hedera;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TransferTransaction;

/**
 * Decides whose account a payment leaves from, and signs for it.
 *
 * <p>The team agreed that business transfers come from the user's own wallet, while audit messages
 * stay signed by the platform. How user keys are held is the Accounts module's decision, so it
 * lives behind this interface: declaring a {@code PaymentSigner} bean (e.g. one that loads the
 * signed-in user's encrypted key) replaces {@link OperatorPaymentSigner} without touching the rest
 * of Payments.
 *
 * <p>A custodial implementation typically sets the transaction id to the user's account (so the
 * user pays the fee), freezes the transaction, and signs it with the user's key.
 */
public interface PaymentSigner {

  /** Account the funds leave from for the current request. */
  AccountId payer(Client client);

  /**
   * Last step before submission. Must return a transaction the network will accept from {@link
   * #payer(Client)}.
   */
  TransferTransaction prepare(TransferTransaction transaction, Client client) throws Exception;
}
