package com.hedera.agentplatform.accounts.hedera;

public interface HederaAccountGateway {
    AccountGatewayResult createAccount(String initialHbar);
    AccountGatewayResult getAccount(String accountId);

    record AccountGatewayResult(String accountId, String status, boolean mock) {}
}
