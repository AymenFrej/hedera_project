package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.dto.AccountResponse;
import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AuthSessionService sessions;
    public AccountService(AccountRepository accounts, AuthSessionService sessions) { this.accounts = accounts; this.sessions = sessions; }
    public List<AccountResponse> findAll() {
        return accounts.findAll().stream().map(a -> new AccountResponse(a.id, a.hederaAccountId, a.balance.toPlainString(), a.status)).toList();
    }
    public AccountResponse current(String authorization) {
        UserEntity user = sessions.require(authorization);
        var account = accounts.findById(user.accountId).orElseThrow();
        return new AccountResponse(account.id, account.hederaAccountId, account.balance.toPlainString(), account.status);
    }
}
