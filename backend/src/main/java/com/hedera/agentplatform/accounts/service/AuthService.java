package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.dto.AccountResponse;
import com.hedera.agentplatform.accounts.dto.AuthRequest;
import com.hedera.agentplatform.accounts.dto.AuthResponse;
import com.hedera.agentplatform.accounts.dto.PasswordChangeRequest;
import com.hedera.agentplatform.accounts.dto.ProfileUpdateRequest;
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
    private final AccountKeyProtector keyProtector;

    public AuthService(UserRepository users, AccountRepository accounts, HederaAccountGateway hedera, AuthSessionService sessions, AccountKeyProtector keyProtector) {
        this.users = users; this.accounts = accounts; this.hedera = hedera; this.sessions = sessions; this.keyProtector = keyProtector;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        validate(request);
        if (users.findByEmailIgnoreCase(request.email().trim()).isPresent()) throw new IllegalArgumentException("Email already registered");
        UserEntity user = new UserEntity();
        user.id = "user_" + UUID.randomUUID(); user.email = request.email().trim().toLowerCase();
        user.displayName = request.displayName() == null || request.displayName().isBlank() ? user.email : request.displayName().trim();
        user.passwordHash = hash(request.password()); user.role = "USER";
        var wallet = hedera.createAccount("0");
        AccountEntity account = new AccountEntity(); account.id = "acct_" + UUID.randomUUID(); account.userId = user.id;
        account.email = user.email; account.hederaAccountId = wallet.accountId(); account.encryptedPrivateKey = keyProtector.encrypt(wallet.privateKey()); account.balance = java.math.BigDecimal.ZERO; account.status = wallet.status();
        user.accountId = account.id; users.save(user); accounts.save(account);
        return response(user, account);
    }

    public AuthResponse login(AuthRequest request) {
        validate(request);
        UserEntity user = users.findByEmailIgnoreCase(request.email().trim()).filter(u -> !"DISABLED".equals(u.role) && u.passwordHash.equals(hash(request.password())))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        return response(user, accounts.findById(user.accountId).orElseThrow());
    }

    public AuthResponse me(String authorization) {
        UserEntity user = sessions.require(authorization);
        return response(user, accounts.findById(user.accountId).orElseThrow(), authorization.substring(7).trim());
    }

    public String startSession(AuthResponse response) { return response.token(); }

    @Transactional
    public AuthResponse updateProfile(String authorization, ProfileUpdateRequest request) {
        UserEntity user = sessions.require(authorization);
        if (request == null || request.email() == null || !request.email().contains("@") || request.displayName() == null || request.displayName().isBlank())
            throw new IllegalArgumentException("A valid email and display name are required");
        users.findByEmailIgnoreCase(request.email().trim()).filter(other -> !other.id.equals(user.id)).ifPresent(other -> { throw new IllegalArgumentException("Email already registered"); });
        user.email = request.email().trim().toLowerCase(); user.displayName = request.displayName().trim(); users.save(user);
        AccountEntity account = accounts.findById(user.accountId).orElseThrow(); account.email = user.email; accounts.save(account);
        return response(user, account, authorization.substring(7).trim());
    }

    @Transactional
    public void changePassword(String authorization, PasswordChangeRequest request) {
        UserEntity user = sessions.require(authorization);
        if (request == null || request.currentPassword() == null || !hash(request.currentPassword()).equals(user.passwordHash)) throw new IllegalArgumentException("Current password is incorrect");
        if (request.newPassword() == null || request.newPassword().length() < 8) throw new IllegalArgumentException("New password must be at least 8 characters");
        user.passwordHash = hash(request.newPassword()); users.save(user); sessions.invalidateUser(user);
    }

    public void logout(String authorization) { sessions.invalidate(authorization); }

    @Transactional
    public void deleteAccount(String authorization) {
        UserEntity user = sessions.require(authorization);
        if ("ADMIN".equals(user.role) || "PLATFORM".equals(user.role))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Ask another administrator to close privileged accounts");
        AccountEntity account = accounts.findById(user.accountId).orElseThrow();
        account.status = "CLOSED"; accounts.save(account);
        user.role = "DISABLED"; users.save(user); sessions.invalidateUser(user);
    }

    private AuthResponse response(UserEntity user, AccountEntity account) {
        return response(user, account, sessions.create(user));
    }

    private AuthResponse response(UserEntity user, AccountEntity account, String token) {
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
