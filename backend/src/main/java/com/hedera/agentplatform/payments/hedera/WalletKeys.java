package com.hedera.agentplatform.payments.hedera;

import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.hashgraph.sdk.PrivateKey;
import java.util.Optional;

/**
 * Where a user's wallet key comes from. Payments only asks; the Accounts module answers.
 *
 * <p>The Accounts module keeps each user's key encrypted. Declaring a {@code WalletKeys} bean that
 * decrypts it for the signed-in user switches payments from the platform operator to the user's own
 * wallet ({@link CustodialPaymentSigner}): the user's account sends the funds and pays the fee. Audit
 * messages are not affected; the platform keeps signing those.
 */
public interface WalletKeys {

  /** The wallet of this actor, or empty when it has none (e.g. the platform itself). */
  Optional<UserWallet> walletOf(Actor actor);

  /** A user's Hedera account and the key that controls it. */
  record UserWallet(String accountId, PrivateKey key) {}
}
