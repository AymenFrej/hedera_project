package com.hedera.agentplatform.accounts.hedera;

import org.springframework.stereotype.Component;

@Component
public class MockHederaAccountGateway implements HederaAccountGateway {
    public AccountGatewayResult createAccount(String initialHbar) { return new AccountGatewayResult("0.0.mock", "CREATED", true); }
    public AccountGatewayResult getAccount(String accountId) { return new AccountGatewayResult(accountId, "ACTIVE", true); }
}
