package com.hedera.agentplatform.payments.hedera;

import com.hedera.agentplatform.payments.hedera.WalletKeys.UserWallet;
import com.hedera.agentplatform.shared.security.ActorResolver;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TransferTransaction;
import java.util.Optional;

/**
 * Pays from the wallet of whoever is making the request.
 *
 * <p>The actor comes from {@link ActorResolver}, i.e. from the server-side session, never from the
 * request. When the actor has a wallet ({@link WalletKeys}), the transaction id is generated for the
 * user's account (so the user pays the fee), the funds leave that account, and the user's key signs.
 * When it has none (the platform itself, or no login yet), the platform operator pays, as before.
 */
public class CustodialPaymentSigner implements PaymentSigner {

  private final WalletKeys wallets;
  private final ActorResolver actors;

  public CustodialPaymentSigner(WalletKeys wallets, ActorResolver actors) {
    this.wallets = wallets;
    this.actors = actors;
  }

  @Override
  public AccountId payer(Client client) {
    return wallet().map(w -> AccountId.fromString(w.accountId())).orElse(client.getOperatorAccountId());
  }

  @Override
  public TransferTransaction prepare(TransferTransaction transaction, Client client) {
    Optional<UserWallet> wallet = wallet();
    if (wallet.isEmpty()) {
      return transaction;
    }
    // The transaction id was generated for the user's account (see payer): freeze, then sign with
    // the user's key. The operator's signature the client adds on execute is harmless extra.
    return transaction.freezeWith(client).sign(wallet.get().key());
  }

  private Optional<UserWallet> wallet() {
    return wallets.walletOf(actors.currentActor());
  }
}
