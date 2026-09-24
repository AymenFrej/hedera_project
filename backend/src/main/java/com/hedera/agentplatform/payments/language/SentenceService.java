package com.hedera.agentplatform.payments.language;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.service.IntentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Sentence → language model → {@link PaymentIntent} → the same resolution as a form.
 *
 * <p>The model only proposes fields. Everything after is the application's: names are resolved
 * from the contacts, assets from the Mirror Node, and the result still goes through preview,
 * policy and audit. Nothing here records or sends anything.
 */
@Service
public class SentenceService {

  static final int MAX_LENGTH = 500;

  private final ObjectProvider<IntentExtractor> extractors;
  private final IntentService intents;

  public SentenceService(ObjectProvider<IntentExtractor> extractors, IntentService intents) {
    this.extractors = extractors;
    this.intents = intents;
  }

  public SentenceInterpretation interpret(String sentence) {
    IntentExtractor extractor = extractors.getIfAvailable();
    if (extractor == null) {
      return new SentenceInterpretation(false,
          "No language model is configured (ANTHROPIC_API_KEY): use the manual request instead.",
          null, null, null, null);
    }
    String text = sentence == null ? "" : sentence.trim();
    if (text.isEmpty() || text.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Write one payment request of at most " + MAX_LENGTH + " characters");
    }

    ExtractedIntent read;
    try {
      read = extractor.extract(text);
    } catch (IntentExtractor.ExtractionException e) {
      return new SentenceInterpretation(true, e.getMessage(), extractor.source(), null, null, null);
    }

    PaymentIntent intent =
        new PaymentIntent(
            blank(read.recipient()),
            blank(read.amount()),
            blank(read.asset()),
            blank(read.envelope()),
            blank(read.memo()),
            blank(read.keepAtLeast()));
    String clarification = blank(read.clarification());

    if (!read.isPayment()) {
      return new SentenceInterpretation(true,
          "This does not read as a payment request.", extractor.source(), intent,
          clarification, null);
    }
    // The model may only repeat an account id the person wrote; it may never supply one.
    if (intent.recipient() != null
        && intent.recipient().matches(CreatePaymentRequest.ACCOUNT_ID)
        && !text.contains(intent.recipient())) {
      return new SentenceInterpretation(true,
          "Refused: the language model proposed account " + intent.recipient()
              + ", which is not in your sentence. Recipients come from your words or your contacts.",
          extractor.source(), null, null, null);
    }

    IntentUnderstanding understanding = intents.understand(intent);
    return new SentenceInterpretation(true,
        understanding.understood() ? "Understood: check it, then preview." : "Not fully understood.",
        extractor.source(), intent, clarification, understanding);
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
