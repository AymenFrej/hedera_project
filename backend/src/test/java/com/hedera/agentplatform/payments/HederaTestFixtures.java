package com.hedera.agentplatform.payments;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenType;
import java.time.Duration;
import java.util.List;

/**
 * Creates the testnet objects a token payment needs. Everything is paid by the operator, which is
 * also the token treasury, so payments can send the token right away.
 */
public final class HederaTestFixtures {

  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private HederaTestFixtures() {}

  /**
   * A fungible token with 0 decimals, so an amount in smallest units reads the same as the amount
   * a person would type. No admin, supply or freeze key: the token is immutable and its supply is
   * fixed, which is all a payment needs.
   */
  public static TokenId createToken(Client client, String name, String symbol, long supply)
      throws Exception {
    return new TokenCreateTransaction()
        .setTokenName(name)
        .setTokenSymbol(symbol)
        .setTokenType(TokenType.FUNGIBLE_COMMON)
        .setDecimals(0)
        .setInitialSupply(supply)
        .setTreasuryAccountId(client.getOperatorAccountId())
        // Token creation costs about $1, above the client's default 2 HBAR cap. This is a
        // ceiling, not a price: only the actual fee is charged.
        .setMaxTransactionFee(new Hbar(30))
        .execute(client, TIMEOUT)
        .getReceipt(client, TIMEOUT)
        .tokenId;
  }

  /**
   * A new account with no automatic token associations, so it can only receive a token once it is
   * explicitly associated.
   */
  public static CreatedAccount createAccount(Client client, Hbar initialBalance) throws Exception {
    PrivateKey key = PrivateKey.generateECDSA();
    AccountId id =
        new AccountCreateTransaction()
            .setKeyWithoutAlias(key.getPublicKey())
            .setInitialBalance(initialBalance)
            .setMaxAutomaticTokenAssociations(0)
            .execute(client, TIMEOUT)
            .getReceipt(client, TIMEOUT)
            .accountId;
    return new CreatedAccount(id, key);
  }

  /** The account itself must sign its association; the operator pays the fee. */
  public static void associate(Client client, CreatedAccount account, TokenId token)
      throws Exception {
    new TokenAssociateTransaction()
        .setAccountId(account.id())
        .setTokenIds(List.of(token))
        .freezeWith(client)
        .sign(account.key())
        .execute(client, TIMEOUT)
        .getReceipt(client, TIMEOUT);
  }

  public record CreatedAccount(AccountId id, PrivateKey key) {}
}
