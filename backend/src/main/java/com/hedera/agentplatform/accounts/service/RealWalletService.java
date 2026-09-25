package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.hedera.HederaAccountGateway;
import com.hedera.agentplatform.accounts.hedera.WalletProvisioningException;
import org.springframework.stereotype.Service;

@Service
public class RealWalletService {
    private final HederaAccountGateway gateway;
    private final AccountKeyProtector keys;
    public RealWalletService(HederaAccountGateway gateway, AccountKeyProtector keys) {
        this.gateway = gateway; this.keys = keys;
    }
    public HederaAccountGateway.AccountGatewayResult createAccount(String initialHbar) {
        // Check secure storage BEFORE sending a network transaction.
        keys.requireConfigured();
        var wallet = gateway.createAccount(initialHbar);
        if (wallet == null || wallet.mock() || wallet.accountId() == null
                || !wallet.accountId().matches("0\\.0\\.[1-9][0-9]*")
                || wallet.privateKey() == null || wallet.privateKey().isBlank()
                || !"ACTIVE".equals(wallet.status())) {
            throw new WalletProvisioningException("Hedera did not return a real active wallet with a signing key. No account was saved or wallet changed.");
        }
        return wallet;
    }
}
