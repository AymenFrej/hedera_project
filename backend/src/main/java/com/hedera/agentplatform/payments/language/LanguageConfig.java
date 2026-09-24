package com.hedera.agentplatform.payments.language;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses which language model reads payment sentences, from PAYMENT_AI_PROVIDER: claude, gemini
 * or ollama. Left blank, Claude is used when ANTHROPIC_API_KEY is set. Without a usable choice
 * there is no {@link IntentExtractor} bean, and the Payments page offers the manual request form.
 */
@Configuration
public class LanguageConfig {

  private static final Logger log = LoggerFactory.getLogger(LanguageConfig.class);

  private static final String PROVIDER = "'${payments.ai.provider:}'.trim().toLowerCase()";

  @Bean
  @ConditionalOnExpression(
      PROVIDER + " == 'claude' or (" + PROVIDER + " == '' and !'${payments.ai.api-key:}'.trim().isEmpty())")
  IntentExtractor claudeIntentExtractor(
      @Value("${payments.ai.api-key:}") String apiKey,
      @Value("${payments.ai.model:claude-opus-5}") String model) {
    requireKey(apiKey, "claude", "ANTHROPIC_API_KEY");
    log.info("Payment sentences are read by {}; the model only proposes a request.", model);
    return new ClaudeIntentExtractor(
        AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).build(), model);
  }

  @Bean
  @ConditionalOnExpression(PROVIDER + " == 'gemini'")
  IntentExtractor geminiIntentExtractor(
      @Value("${payments.ai.gemini.api-key:}") String apiKey,
      @Value("${payments.ai.gemini.model:gemini-flash-lite-latest}") String model) {
    requireKey(apiKey, "gemini", "GEMINI_API_KEY");
    log.info("Payment sentences are read by {} (Gemini); the model only proposes a request.", model);
    return new GeminiIntentExtractor(GeminiIntentExtractor.DEFAULT_BASE_URL, apiKey.trim(), model);
  }

  @Bean
  @ConditionalOnExpression(PROVIDER + " == 'ollama'")
  IntentExtractor ollamaIntentExtractor(
      @Value("${payments.ai.ollama.url:http://localhost:11434}") String url,
      @Value("${payments.ai.ollama.model:qwen2.5:3b}") String model) {
    log.info("Payment sentences are read by {} in Ollama at {}; the model only proposes a request.", model, url);
    return new OllamaIntentExtractor(url, model);
  }

  private static void requireKey(String key, String provider, String variable) {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(
          "PAYMENT_AI_PROVIDER=" + provider + " needs " + variable + " in .env");
    }
  }
}
