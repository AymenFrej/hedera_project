package com.hedera.agentplatform.payments.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the fallback {@link PaymentPolicy}. Declaring a {@code PaymentPolicy} bean anywhere
 * else (the Policies module) replaces it.
 */
@Configuration
public class PaymentPolicyConfig {

  private static final Logger log = LoggerFactory.getLogger(PaymentPolicyConfig.class);

  @Bean
  @ConditionalOnMissingBean(PaymentPolicy.class)
  PaymentPolicy noPolicyConfigured() {
    log.warn(
        "No PaymentPolicy bean: payments are NOT checked against any policy. "
            + "The Policies module can provide one by declaring a PaymentPolicy bean.");
    return new NoPolicyConfigured();
  }
}
