package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.dto.CreateManagedUserRequest;
import com.hedera.agentplatform.accounts.dto.ManagedUserResponse;
import com.hedera.agentplatform.accounts.dto.RoleUpdateRequest;
import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserRepository users; private final AccountRepository accounts; private final AuthSessionService sessions; private final RealWalletService hedera; private final AccountKeyProtector keys;
    public AdminUserService(UserRepository users, AccountRepository accounts, AuthSessionService sessions, RealWalletService hedera, AccountKeyProtector keys) { this.users=users;this.accounts=accounts;this.sessions=sessions;this.hedera=hedera;this.keys=keys; }
    public List<ManagedUserResponse> list(String auth) { requireAdmin(auth); return users.findAll().stream().map(this::map).toList(); }
    @Transactional public ManagedUserResponse create(String auth, CreateManagedUserRequest request) {
        requireAdmin(auth); if (request == null || request.email()==null || !request.email().contains("@") || request.password()==null || request.password().length()<8) throw new IllegalArgumentException("Valid email and password of at least 8 characters are required");
        String role=normalizeRole(request.role()); if (users.findByEmailIgnoreCase(request.email().trim()).isPresent()) throw new IllegalArgumentException("Email already registered");
        UserEntity user=new UserEntity(); user.id="user_"+UUID.randomUUID();user.email=request.email().trim().toLowerCase();user.displayName=request.displayName()==null||request.displayName().isBlank()?user.email:request.displayName().trim();user.passwordHash=hash(request.password());user.role=role;
        var wallet=hedera.createAccount("0"); AccountEntity account=new AccountEntity();account.id="acct_"+UUID.randomUUID();account.userId=user.id;account.email=user.email;account.hederaAccountId=wallet.accountId();account.encryptedPrivateKey=keys.encrypt(wallet.privateKey());account.balance=BigDecimal.ZERO;account.status=wallet.status();user.accountId=account.id;users.save(user);accounts.save(account);return map(user);
    }
    @Transactional public ManagedUserResponse updateRole(String auth,String id,RoleUpdateRequest request){ UserEntity actor=requireAdmin(auth); if(actor.id.equals(id)) throw new IllegalArgumentException("You cannot change your own role"); UserEntity user=target(id); if("DISABLED".equals(user.role)) throw new IllegalArgumentException("Account is closed"); user.role=normalizeRole(request==null?null:request.role()); sessions.invalidateUser(user); return map(users.save(user)); }
    @Transactional public void delete(String auth,String id){ UserEntity actor=requireAdmin(auth); if(actor.id.equals(id)) throw new IllegalArgumentException("An administrator cannot delete their own account"); UserEntity user=target(id); sessions.invalidateUser(user); AccountEntity account=accounts.findById(user.accountId).orElseThrow(); account.status="CLOSED"; accounts.save(account); user.role="DISABLED"; users.save(user); }
    private UserEntity requireAdmin(String auth){ UserEntity user=sessions.require(auth); if(!"ADMIN".equals(user.role)&&!"PLATFORM".equals(user.role)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Administrator access required"); return user; }
    @Transactional
    public ManagedUserResponse restore(String auth, String id, RoleUpdateRequest request) {
        requireAdmin(auth);
        UserEntity user = target(id);
        if (!"DISABLED".equals(user.role)) throw new IllegalArgumentException("Account is already active");
        user.role = normalizeRole(request == null ? null : request.role());
        AccountEntity account = accounts.findById(user.accountId).orElseThrow();
        account.status = "ACTIVE";
        accounts.save(account); users.save(user); sessions.invalidateUser(user);
        return map(user);
    }
    @Transactional
    public ManagedUserResponse updateProfile(String auth, String id, com.hedera.agentplatform.accounts.dto.ProfileUpdateRequest request) {
        requireAdmin(auth); UserEntity user = target(id);
        if (request == null || request.email() == null || !request.email().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
            || request.email().trim().length() > 320 || request.displayName() == null
            || request.displayName().isBlank() || request.displayName().trim().length() > 128)
            throw new IllegalArgumentException("A valid email and display name (up to 128 characters) are required");
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        users.findByEmailIgnoreCase(email).filter(other -> !other.id.equals(id)).ifPresent(other -> {
            throw new IllegalArgumentException("Email already registered");
        });
        user.email = email; user.displayName = request.displayName().trim();
        AccountEntity account = accounts.findById(user.accountId).orElseThrow(); account.email = email;
        accounts.save(account); users.save(user); return map(user);
    }
    private UserEntity target(String id) {
        return users.findForUpdate(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    }
    private ManagedUserResponse map(UserEntity user){ AccountEntity a=accounts.findById(user.accountId).orElseThrow(); return new ManagedUserResponse(user.id,user.email,user.displayName,user.role,a.id,a.hederaAccountId); }
    private static String normalizeRole(String role){ if(role==null) throw new IllegalArgumentException("Role is required"); String r=role.trim().toUpperCase(); if(!List.of("USER","ADMIN","AUDITOR","PLATFORM").contains(r)) throw new IllegalArgumentException("Unsupported role"); return r; }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
