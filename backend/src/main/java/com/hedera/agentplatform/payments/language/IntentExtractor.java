package com.hedera.agentplatform.payments.language;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Turns a sentence, or an attached document, into the fields of a payment request.
 * Implementations may use a language model; whatever they return is only a proposal that goes
 * through the same resolution, validation, policy and preview as a form.
 */
public interface IntentExtractor {

  /** The fields the sentence states; throws {@link ExtractionException} when it cannot answer. */
  ExtractedIntent extract(String sentence);

  /** Which model or method produced the extraction, shown to the user. */
  String source();

  /** The document types (MIME) this model can read; empty when it reads no documents. */
  default Set<String> documentTypes() {
    return Set.of();
  }

  /** The payment a document asks for, with the person's optional note; see {@link DocumentPrompt}. */
  default ExtractedIntent extractFromDocument(Document document, String note) {
    throw new ExtractionException(source() + " cannot read documents");
  }

  /** A file the person attached: an invoice, a bill, a payment request. Already validated. */
  record Document(String name, String mimeType, byte[] data) {
    public boolean isText() {
      return mimeType.startsWith("text/");
    }

    public String text() {
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  class ExtractionException extends RuntimeException {
    public ExtractionException(String message) {
      super(message);
    }

    public ExtractionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
