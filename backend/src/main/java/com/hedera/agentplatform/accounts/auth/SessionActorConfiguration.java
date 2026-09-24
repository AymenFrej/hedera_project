package com.hedera.agentplatform.accounts.auth;

import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class SessionActorConfiguration {
    @Bean
    ActorResolver sessionActorResolver(AuthSessionService sessions, AccountRepository accounts) {
        return () -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader("Authorization");
                if (authorization != null) {
                    var user = sessions.require(authorization);
                    String wallet = accounts.findById(user.accountId).map(a -> a.hederaAccountId).orElse(null);
                    return Actor.user(user.id, wallet);
                }
            }
            return Actor.system("platform");
        };
    }
}
