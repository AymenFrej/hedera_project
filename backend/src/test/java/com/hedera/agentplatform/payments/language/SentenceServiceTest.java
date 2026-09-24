package com.hedera.agentplatform.payments.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The language model is replaced by a stand-in: what is tested is what the application does with
 * whatever the model proposes. The model itself is never trusted to supply an account id.
 */
@SpringBootTest
@Transactional
class SentenceServiceTest {

  private static final String ZIED = "0.0.10682427";

  @Autowired private SentenceService sentences;
  @Autowired private ContactService contacts;
  @MockitoBean private IntentExtractor model;

  @BeforeEach
  void setUp() {
    when(model.source()).thenReturn("stand-in model");
    contacts.add("Zied", ZIED);
  }

  private void modelReads(boolean payment, String recipient, String amount, String keep, String note) {
    when(model.extract(anyString()))
        .thenReturn(new ExtractedIntent(payment, recipient, amount, "HBAR", "essentials", keep, "", note));
  }

  @Test
  void a_sentence_is_read_by_the_model_and_the_name_resolved_from_contacts() {
    modelReads(true, "Zied", "5", "100", "");

    SentenceInterpretation i = sentences.interpret("Pay Zied 5 HBAR, keep at least 100 HBAR");

    assertThat(i.available()).isTrue();
    assertThat(i.intent().recipient()).isEqualTo("Zied");
    assertThat(i.understanding().understood()).isTrue();
    assertThat(i.understanding().request().destination()).as("from the contacts").isEqualTo(ZIED);
    assertThat(i.understanding().request().keepAtLeast()).isEqualTo("100");
  }

  @Test
  void an_account_id_the_person_did_not_write_is_refused() {
    modelReads(true, "0.0.666", "5", "", "");

    SentenceInterpretation i = sentences.interpret("Pay my landlord 5 HBAR");

    assertThat(i.intent()).isNull();
    assertThat(i.understanding()).isNull();
    assertThat(i.detail()).contains("not in your sentence");
  }

  @Test
  void an_account_id_the_person_did_write_is_kept() {
    modelReads(true, "0.0.4242", "5", "", "");

    SentenceInterpretation i = sentences.interpret("Send 5 HBAR to 0.0.4242");

    assertThat(i.understanding().request().destination()).isEqualTo("0.0.4242");
  }

  @Test
  void a_name_that_is_not_a_contact_is_not_guessed() {
    modelReads(true, "Bob", "5", "", "");

    SentenceInterpretation i = sentences.interpret("Pay Bob 5 HBAR");

    assertThat(i.understanding().understood()).isFalse();
    assertThat(i.understanding().problems()).contains("\"Bob\" is not one of your contacts");
  }

  @Test
  void a_sentence_that_is_not_a_payment_resolves_nothing() {
    modelReads(false, "", "", "", "This asks for the weather, not a payment.");

    SentenceInterpretation i = sentences.interpret("What is the weather today?");

    assertThat(i.understanding()).isNull();
    assertThat(i.clarification()).contains("not a payment");
  }

  @Test
  void a_model_failure_is_reported_not_hidden() {
    when(model.extract(anyString()))
        .thenThrow(new IntentExtractor.ExtractionException("The language model could not be reached"));

    SentenceInterpretation i = sentences.interpret("Pay Zied 5 HBAR");

    assertThat(i.available()).isTrue();
    assertThat(i.detail()).contains("could not be reached");
    assertThat(i.understanding()).isNull();
  }

  @Test
  void an_empty_or_overlong_sentence_is_refused() {
    assertThatThrownBy(() -> sentences.interpret("  ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sentences.interpret("x".repeat(501)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
