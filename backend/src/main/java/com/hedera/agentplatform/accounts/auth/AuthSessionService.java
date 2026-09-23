package com.hedera.agentplatform.accounts.auth;

import com.hedera.agentplatform.accounts.entity.UserEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Starter session service. Replace with Spring Security/JWT before production. */
@Service
public class AuthSessionService {
    private final Map<String, UserEntity> sessions = new ConcurrentHashMap<>();

    public String create(UserEntity user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        return token;
    }

    public UserEntity require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authentication required");
        }
        UserEntity user = sessions.get(authorization.substring("Bearer ".length()).trim());
        if (user == null) throw new IllegalArgumentException("Invalid session");
        return user;
    }

    public void invalidate(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) sessions.remove(authorization.substring("Bearer ".length()).trim());
    }

    public void invalidateUser(UserEntity user) { sessions.entrySet().removeIf(entry -> entry.getValue().id.equals(user.id)); }
}
