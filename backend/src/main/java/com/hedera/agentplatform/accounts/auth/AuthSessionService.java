package com.hedera.agentplatform.accounts.auth;

import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthSessionService {
    private record Session(String userId, Instant expires) {}
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final UserRepository users;
    public AuthSessionService(UserRepository users) { this.users = users; }
    public String create(UserEntity user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(user.id, Instant.now().plusSeconds(28800)));
        return token;
    }
    public UserEntity require(String authorization) {
        String token = token(authorization);
        Session session = sessions.get(token);
        if (session == null || session.expires().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in.");
        }
        return users.findById(session.userId()).filter(u -> !"DISABLED".equals(u.role))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
    }
    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in");
        return authorization.substring(7).trim();
    }
    public void invalidate(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) sessions.remove(authorization.substring(7).trim());
    }
    public void invalidateUser(UserEntity user) { sessions.entrySet().removeIf(e -> e.getValue().userId().equals(user.id)); }
}
