package com.hedera.agentplatform.accounts.hedera;

/** The app may start offline, but new accounts must never receive fabricated wallets. */
public class UnavailableHederaAccountGateway implements HederaAccountGateway {
    public AccountGatewayResult createAccount(String initialHbar) {
        throw new WalletProvisioningException("Real wallet creation is unavailable: the backend needs Hedera operator credentials. No account was created or wallet changed. Ask the administrator to configure and restart the backend.");
    }
    public AccountGatewayResult getAccount(String accountId) {
        throw new WalletProvisioningException("Hedera account lookup is unavailable: configure the backend Hedera credentials.");
    }
}
