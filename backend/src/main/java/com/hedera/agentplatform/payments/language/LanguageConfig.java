package com.hedera.agentplatform.payments.language;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables reading payment sentences with Claude when an API key is configured. Without one there is
 * no {@link IntentExtractor} bean, and the Payments page offers the manual request form instead.
 */
@Configuration
public class LanguageConfig {

  private static final Logger log = LoggerFactory.getLogger(LanguageConfig.class);

  @Bean
  @ConditionalOnExpression("!'${payments.ai.api-key:}'.trim().isEmpty()")
  IntentExtractor claudeIntentExtractor(
      @Value("${payments.ai.api-key}") String apiKey,
      @Value("${payments.ai.model:claude-opus-5}") String model) {
    log.info("Payment sentences are read by {}; the model only proposes a request.", model);
    return new ClaudeIntentExtractor(
        AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).build(), model);
  }
}
