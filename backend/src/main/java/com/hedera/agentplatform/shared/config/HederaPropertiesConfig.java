package com.hedera.agentplatform.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HederaProperties} unconditionally.
 *
 * <p>Kept separate from {@link HederaClientConfig}: that one is conditional on credentials being
 * present, and a conditional class also skips its {@code @EnableConfigurationProperties}. Binding
 * the properties there meant that without credentials the properties bean did not exist either,
 * and every component depending on it failed to start — the opposite of the intended fallback.
 */
@Configuration
@EnableConfigurationProperties(HederaProperties.class)
public class HederaPropertiesConfig {}
