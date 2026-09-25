package com.hedera.agentplatform.tokens.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.language.IntentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The model is a stand-in: what is tested is what the Studio does with whatever it proposes. */
@SpringBootTest
class TokenSentenceServiceTest {

  @Autowired private TokenSentenceService sentences;
  @MockitoBean private IntentExtractor model;

  @BeforeEach
  void setUp() {
    when(model.source()).thenReturn("stand-in model");
  }

  private void modelReads(ExtractedTokenSpec spec) {
    when(model.extractAs(anyString(), anyString(), eq(ExtractedTokenSpec.class))).thenReturn(spec);
  }

  @Test
  void a_complete_sentence_fills_the_studio_and_previews_its_promises() {
    modelReads(new ExtractedTokenSpec(true, "Coffee Beans", "BEAN", "0", "1000000", "FIXED", "", "", ""));

    var i = sentences.interpret("Create a loyalty token called Coffee Beans, BEAN, 1 million, fixed forever, no decimals");

    assertThat(i.draft().symbol()).isEqualTo("BEAN");
    assertThat(i.preview().valid()).isTrue();
    assertThat(i.preview().promises()).anyMatch(p -> p.title().equals("Supply fixed forever at 1000000 BEAN"));
  }

  @Test
  void what_the_sentence_does_not_say_is_left_for_the_person_not_guessed() {
    modelReads(new ExtractedTokenSpec(true, "Coffee Beans", "", "", "1000", "", "", "", "No symbol or decimals given."));

    var i = sentences.interpret("Make me a Coffee Beans token with 1000 of them");

    assertThat(i.draft().symbol()).isNull();
    assertThat(i.preview().valid()).isFalse();
    assertThat(i.detail()).contains("complete them in the Studio");
    assertThat(i.clarification()).contains("No symbol");
  }

  @Test
  void a_sentence_that_is_not_about_a_token_creates_nothing() {
    modelReads(new ExtractedTokenSpec(false, "", "", "", "", "", "", "", "This is about the weather."));

    var i = sentences.interpret("Will it rain tomorrow?");

    assertThat(i.preview()).isNull();
    assertThat(i.detail()).contains("does not read as a request to create a token");
  }
}
