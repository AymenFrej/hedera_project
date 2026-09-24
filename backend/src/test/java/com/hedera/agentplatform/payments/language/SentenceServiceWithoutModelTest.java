package com.hedera.agentplatform.payments.language;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Without an API key the feature is off, and says so, instead of failing the application. */
@SpringBootTest
class SentenceServiceWithoutModelTest {

  @Autowired private SentenceService sentences;

  @Test
  void without_a_key_the_sentence_box_reports_it_is_not_configured() {
    SentenceInterpretation i = sentences.interpret("Pay Zied 5 HBAR");

    assertThat(i.available()).isFalse();
    assertThat(i.detail()).contains("ANTHROPIC_API_KEY");
  }
}
