package com.hedera.agentplatform.payments.language;

/**
 * Turns a sentence into the fields of a payment request. Implementations may use a language model;
 * whatever they return is only a proposal that goes through the same resolution, validation,
 * policy and preview as a form.
 */
public interface IntentExtractor {

  /** The fields the sentence states; throws {@link ExtractionException} when it cannot answer. */
  ExtractedIntent extract(String sentence);

  /** Which model or method produced the extraction, shown to the user. */
  String source();

  class ExtractionException extends RuntimeException {
    public ExtractionException(String message) {
      super(message);
    }

    public ExtractionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
