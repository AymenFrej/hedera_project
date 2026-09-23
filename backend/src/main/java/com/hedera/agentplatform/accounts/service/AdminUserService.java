package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.dto.CreateManagedUserRequest;
import com.hedera.agentplatform.accounts.dto.ManagedUserResponse;
import com.hedera.agentplatform.accounts.dto.RoleUpdateRequest;
import com.hedera.agentplatform.accounts.entity.AccountEntity;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.hedera.HederaAccountGateway;
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
    private final UserRepository users; private final AccountRepository accounts; private final AuthSessionService sessions; private final HederaAccountGateway hedera; private final AccountKeyProtector keys;
    public AdminUserService(UserRepository users, AccountRepository accounts, AuthSessionService sessions, HederaAccountGateway hedera, AccountKeyProtector keys) { this.users=users;this.accounts=accounts;this.sessions=sessions;this.hedera=hedera;this.keys=keys; }
    public List<ManagedUserResponse> list(String auth) { requireAdmin(auth); return users.findAll().stream().map(this::map).toList(); }
    @Transactional public ManagedUserResponse create(String auth, CreateManagedUserRequest request) {
        requireAdmin(auth); if (request == null || request.email()==null || !request.email().contains("@") || request.password()==null || request.password().length()<8) throw new IllegalArgumentException("Valid email and password of at least 8 characters are required");
        String role=normalizeRole(request.role()); if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw new IllegalArgumentException("Email already registered");
        UserEntity user=new UserEntity(); user.id="user_"+UUID.randomUUID();user.email=request.email().trim().toLowerCase();user.displayName=request.displayName()==null||request.displayName().isBlank()?user.email:request.displayName().trim();user.passwordHash=hash(request.password());user.role=role;
        var wallet=hedera.createAccount("0"); AccountEntity account=new AccountEntity();account.id="acct_"+UUID.randomUUID();account.userId=user.id;account.email=user.email;account.hederaAccountId=wallet.accountId();account.encryptedPrivateKey=keys.encrypt(wallet.privateKey());account.balance=BigDecimal.ZERO;account.status=wallet.status();user.accountId=account.id;users.save(user);accounts.save(account);return map(user);
    }
    @Transactional public ManagedUserResponse updateRole(String auth,String id,RoleUpdateRequest request){ requireAdmin(auth); UserEntity user=users.findById(id).orElseThrow(); user.role=normalizeRole(request==null?null:request.role()); return map(users.save(user)); }
    @Transactional public void delete(String auth,String id){ UserEntity actor=requireAdmin(auth); if(actor.id.equals(id)) throw new IllegalArgumentException("An administrator cannot delete their own account"); UserEntity user=users.findById(id).orElseThrow(); sessions.invalidateUser(user); accounts.deleteById(user.accountId);users.deleteById(user.id); }
    private UserEntity requireAdmin(String auth){ UserEntity user=sessions.require(auth); if(!"ADMIN".equals(user.role)&&!"PLATFORM".equals(user.role)) throw new IllegalArgumentException("Administrator access required"); return user; }
    private ManagedUserResponse map(UserEntity user){ AccountEntity a=accounts.findById(user.accountId).orElseThrow(); return new ManagedUserResponse(user.id,user.email,user.displayName,user.role,a.id,a.hederaAccountId); }
    private static String normalizeRole(String role){ if(role==null) throw new IllegalArgumentException("Role is required"); String r=role.trim().toUpperCase(); if(!List.of("USER","ADMIN","AUDITOR","PLATFORM").contains(r)) throw new IllegalArgumentException("Unsupported role"); return r; }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
