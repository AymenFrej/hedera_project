package com.hedera.agentplatform.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the fallback {@link ActorResolver}.
 *
 * <p>Declaring an {@code ActorResolver} bean anywhere else (the Accounts module, once login exists)
 * replaces this one. Using {@code @ConditionalOnMissingBean} on a {@code @Configuration} method is
 * the reliable form; on a plain {@code @Component} it depends on scan order.
 */
@Configuration
public class ActorResolverConfig {

    private static final Logger log = LoggerFactory.getLogger(ActorResolverConfig.class);

    @Bean
    @ConditionalOnMissingBean(ActorResolver.class)
    ActorResolver systemActorResolver() {
        log.info(
                "No authentication yet: audit events are attributed to SYSTEM/platform. "
                        + "The Accounts module can replace this by declaring an ActorResolver bean.");
        return new SystemActorResolver();
    }
}
