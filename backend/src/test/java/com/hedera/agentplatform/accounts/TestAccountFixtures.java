package com.hedera.agentplatform.accounts;

import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;

public final class TestAccountFixtures {
    private TestAccountFixtures() {}

    public static void ensure(UserRepository users, AccountRepository accounts) {
        for (var fixture : List.of(
                new Fixture("user_demo", "user@example.test", "USER", "0.0.100001"),
                new Fixture("admin_demo", "admin@example.test", "ADMIN", "0.0.100002"),
                new Fixture("auditor_demo", "auditor@example.test", "AUDITOR", "0.0.100003"),
                new Fixture("platform_demo", "platform@example.test", "PLATFORM", "0.0.100004"))) {
            if (users.existsById(fixture.userId())) continue;
            var account = new AccountEntity();
            account.id = "acct_test_" + fixture.userId();
            account.userId = fixture.userId();
            account.email = fixture.email();
            account.hederaAccountId = fixture.accountId();
            account.encryptedPrivateKey = "test-encrypted-key";
            account.balance = BigDecimal.ZERO;
            account.status = "ACTIVE";
            accounts.save(account);

            var user = new UserEntity();
            user.id = fixture.userId();
            user.email = fixture.email();
            user.displayName = fixture.role() + " test user";
            user.passwordHash = "test-password-hash";
            user.role = fixture.role();
            user.accountId = account.id;
            users.save(user);
        }
        users.flush();
    }

    private record Fixture(String userId, String email, String role, String accountId) {}
}