package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.dto.AccountResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    public List<AccountResponse> findAll() {
        return List.of(new AccountResponse("acct_demo", "0.0.12345", "5.00", "MOCK"));
    }
}
