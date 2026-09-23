package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.dto.AccountResponse;
import com.hedera.agentplatform.accounts.dto.AuthRequest;
import com.hedera.agentplatform.accounts.dto.AuthResponse;
import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.hedera.HederaAccountGateway;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final HederaAccountGateway hedera;
    private final AuthSessionService sessions;

    public AuthService(UserRepository users, AccountRepository accounts, HederaAccountGateway hedera, AuthSessionService sessions) {
        this.users = users; this.accounts = accounts; this.hedera = hedera; this.sessions = sessions;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        validate(request);
        if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw new IllegalArgumentException("Email already registered");
        UserEntity user = new UserEntity();
        user.id = "user_" + UUID.randomUUID(); user.email = request.email().trim().toLowerCase();
        user.displayName = request.displayName() == null || request.displayName().isBlank() ? user.email : request.displayName().trim();
        user.passwordHash = hash(request.password()); user.role = "USER";
        var wallet = hedera.createAccount("0");
        AccountEntity account = new AccountEntity(); account.id = "acct_" + UUID.randomUUID(); account.userId = user.id;
        account.email = user.email; account.hederaAccountId = wallet.accountId(); account.balance = java.math.BigDecimal.ZERO; account.status = wallet.status();
        user.accountId = account.id; users.save(user); accounts.save(account);
        return response(user, account);
    }

    public AuthResponse login(AuthRequest request) {
        validate(request);
        UserEntity user = users.findByEmailIgnoreCase(request.email()).filter(u -> u.passwordHash.equals(hash(request.password())))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        return response(user, accounts.findById(user.accountId).orElseThrow());
    }

    public AuthResponse me(String authorization) {
        UserEntity user = sessions.require(authorization);
        return response(user, accounts.findById(user.accountId).orElseThrow());
    }

    public String startSession(AuthResponse response) { return response.token(); }

    private AuthResponse response(UserEntity user, AccountEntity account) {
        String token = sessions.create(user);
        return new AuthResponse(token, user.id, user.email, user.displayName, user.role,
                new AccountResponse(account.id, account.hederaAccountId, account.balance.toPlainString(), account.status));
    }

    private static void validate(AuthRequest request) {
        if (request == null || request.email() == null || !request.email().contains("@") || request.password() == null || request.password().length() < 8)
            throw new IllegalArgumentException("A valid email and password of at least 8 characters are required");
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
