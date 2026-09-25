package com.hedera.agentplatform.payments.language;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** PAYMENT_AI_PROVIDER picks exactly one model, and a blank setting keeps the old behaviour. */
class LanguageProviderTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(LanguageConfig.class);

  @Test
  void nothing_set_means_no_model() {
    context.run(c -> assertThat(c).doesNotHaveBean(IntentExtractor.class));
  }

  @Test
  void a_claude_key_alone_still_selects_claude() {
    context
        .withPropertyValues("payments.ai.api-key=sk-ant-test")
        .run(c -> assertThat(c.getBean(IntentExtractor.class)).isInstanceOf(ClaudeIntentExtractor.class));
  }

  @Test
  void ollama_is_chosen_even_when_a_claude_key_is_present() {
    context
        .withPropertyValues("payments.ai.provider=ollama", "payments.ai.api-key=sk-ant-test")
        .run(c -> {
          assertThat(c).hasSingleBean(IntentExtractor.class);
          assertThat(c.getBean(IntentExtractor.class)).isInstanceOf(OllamaIntentExtractor.class);
          assertThat(c.getBean(IntentExtractor.class).source()).contains("qwen2.5:3b");
        });
  }

  @Test
  void gemini_is_chosen_with_its_key() {
    context
        .withPropertyValues("payments.ai.provider=Gemini", "payments.ai.gemini.api-key=g-test")
        .run(c -> assertThat(c.getBean(IntentExtractor.class)).isInstanceOf(GeminiIntentExtractor.class));
  }

  @Test
  void gemini_without_its_key_fails_at_startup_with_the_reason() {
    context
        .withPropertyValues("payments.ai.provider=gemini")
        .run(c -> assertThat(c).getFailure().rootCause().hasMessageContaining("GEMINI_API_KEY"));
  }
}
