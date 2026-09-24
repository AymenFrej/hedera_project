package com.hedera.agentplatform.accounts.hedera;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import java.time.Duration;

/** Real testnet account creation. The operator pays the creation transaction. */
public class SdkHederaAccountGateway implements HederaAccountGateway {
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private final Client client;
    public SdkHederaAccountGateway(Client client) { this.client = client; }

    @Override
    public AccountGatewayResult createAccount(String initialHbar) {
        try {
            PrivateKey key = PrivateKey.generateED25519();
            TransactionReceipt receipt = new AccountCreateTransaction()
                    .setKey(key.getPublicKey())
                    .setInitialBalance(Hbar.fromString(initialHbar == null || initialHbar.isBlank() ? "0" : initialHbar))
                    .execute(client, TIMEOUT)
                    .getReceipt(client, TIMEOUT);
            AccountId accountId = receipt.accountId;
            return new AccountGatewayResult(accountId.toString(), "ACTIVE", false, key.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Hedera account", e);
        }
    }

    @Override
    public AccountGatewayResult getAccount(String accountId) {
        try {
            new com.hedera.hashgraph.sdk.AccountInfoQuery().setAccountId(AccountId.fromString(accountId)).execute(client, TIMEOUT);
            return new AccountGatewayResult(accountId, "ACTIVE", false);
        } catch (Exception e) { throw new IllegalStateException("Unable to read Hedera account " + accountId, e); }
    }
}
