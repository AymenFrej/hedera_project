package com.hedera.agentplatform.payments.hedera;

import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import com.hedera.agentplatform.accounts.service.AccountKeyProtector;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.ActorType;
import com.hedera.hashgraph.sdk.PrivateKey;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The signed-in person's wallet, as the Accounts module stores it: its account id, and its key
 * unlocked by {@link AccountKeyProtector}. With this bean, payments leave from the requester's own
 * wallet ({@link CustodialPaymentSigner}); the platform (a job, a treasury top-up) has none and pays
 * from the operator. Set {@code PAYMENTS_PAY_FROM=platform} to go back to the operator for everyone.
 *
 * <p>A person without a usable wallet is refused rather than silently paid for by the platform.
 */
@Component
@ConditionalOnProperty(name = "payments.pay-from", havingValue = "user", matchIfMissing = true)
public class AccountsWalletKeys implements WalletKeys {

  private static final String REAL_ACCOUNT = "^0\\.0\\.[1-9]\\d*$";

  private final UserRepository users;
  private final AccountRepository accounts;
  private final AccountKeyProtector keys;

  public AccountsWalletKeys(UserRepository users, AccountRepository accounts, AccountKeyProtector keys) {
    this.users = users;
    this.accounts = accounts;
    this.keys = keys;
  }

  @Override
  public Optional<String> accountOf(Actor actor) {
    return account(actor).map(a -> a.hederaAccountId);
  }

  @Override
  public Optional<UserWallet> walletOf(Actor actor) {
    return account(actor).map(a -> {
      String raw = keys.decrypt(a.encryptedPrivateKey);
      if (raw == null) {
        throw new IllegalStateException("Your wallet " + a.hederaAccountId + " has no signing key: nothing can be sent from it");
      }
      return new UserWallet(a.hederaAccountId, PrivateKey.fromString(raw));
    });
  }

  /** The person's real wallet; empty for the platform itself. */
  private Optional<AccountEntity> account(Actor actor) {
    if (actor == null || actor.type() != ActorType.USER) {
      return Optional.empty();
    }
    var user = users.findById(actor.id())
        .orElseThrow(() -> new IllegalStateException("Unknown user " + actor.id()));
    AccountEntity account = user.accountId == null ? null : accounts.findById(user.accountId).orElse(null);
    if (account == null || account.hederaAccountId == null || !account.hederaAccountId.matches(REAL_ACCOUNT)) {
      throw new IllegalStateException("You have no Hedera wallet yet: nothing can be sent for you");
    }
    return Optional.of(account);
  }
}
