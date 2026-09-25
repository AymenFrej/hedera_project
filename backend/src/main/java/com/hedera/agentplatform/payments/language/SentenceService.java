package com.hedera.agentplatform.payments.language;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.service.ContactService;
import com.hedera.agentplatform.payments.service.IntentService;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Sentence or document → language model → {@link PaymentIntent} → the same resolution as a form.
 *
 * <p>The model only proposes fields. Everything after is the application's: names are resolved
 * from the contacts, assets from the Mirror Node, and the result still goes through preview,
 * policy and audit. Nothing here records or sends anything.
 */
@Service
public class SentenceService {

  static final int MAX_LENGTH = 500;
  static final int MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
  private static final String NOT_CONFIGURED =
      "No language model is configured (PAYMENT_AI_PROVIDER: claude, gemini or ollama):"
          + " use the manual request instead.";

  private final ObjectProvider<IntentExtractor> extractors;
  private final IntentService intents;
  private final ContactService contacts;

  public SentenceService(
      ObjectProvider<IntentExtractor> extractors, IntentService intents, ContactService contacts) {
    this.extractors = extractors;
    this.intents = intents;
    this.contacts = contacts;
  }

  public SentenceInterpretation interpret(String sentence) {
    IntentExtractor extractor = extractors.getIfAvailable();
    if (extractor == null) {
      return new SentenceInterpretation(false, NOT_CONFIGURED, null, null, null, null, null, null);
    }
    String text = sentence == null ? "" : sentence.trim();
    if (text.isEmpty() || text.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Write one payment request of at most " + MAX_LENGTH + " characters");
    }

    ExtractedIntent read;
    try {
      read = extractor.extract(text);
    } catch (IntentExtractor.ExtractionException e) {
      return new SentenceInterpretation(true, e.getMessage(), extractor.source(), null, null, null, null, null);
    }
    return resolve(extractor, read, text, null, false);
  }

  /**
   * Reads the payment an attached document asks for. The document is untrusted: its type is
   * checked from its bytes, and an account id the model returns must be printed in it (text) or
   * is flagged for the person to compare with the document (PDF, image).
   */
  public SentenceInterpretation interpretDocument(
      String fileName, String mimeType, String base64, String note) {
    IntentExtractor extractor = extractors.getIfAvailable();
    if (extractor == null) {
      return new SentenceInterpretation(false, NOT_CONFIGURED, null, null, null, null, null, null);
    }
    String name = cleanName(fileName);
    String type = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
    String cleanNote = note == null ? "" : note.trim();
    if (cleanNote.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("The note can be at most " + MAX_LENGTH + " characters");
    }
    byte[] data;
    try {
      data = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The document could not be read");
    }
    if (data.length == 0) {
      throw new IllegalArgumentException("The document is empty");
    }
    if (data.length > MAX_DOCUMENT_BYTES) {
      throw new IllegalArgumentException("The document is larger than 5 MB");
    }
    if (!isSupported(type)) {
      throw new IllegalArgumentException("Attach a PDF, an image (PNG, JPEG, WebP) or a text file");
    }
    if (!contentMatches(type, data)) {
      throw new IllegalArgumentException("The file's content does not match its type (" + type + ")");
    }
    if (!extractor.documentTypes().contains(type)) {
      return new SentenceInterpretation(true,
          extractor.source() + " cannot read this type of document (" + type + "). "
              + (DocumentPrompt.TEXT_TYPES.contains(type) ? "" : "Use Gemini or Claude, or ")
              + "type the request instead.",
          extractor.source(), null, null, null, name, null);
    }

    IntentExtractor.Document document = new IntentExtractor.Document(name, type, data);
    ExtractedIntent read;
    try {
      read = extractor.extractFromDocument(document, cleanNote);
    } catch (IntentExtractor.ExtractionException e) {
      return new SentenceInterpretation(true, e.getMessage(), extractor.source(), null, null, null, name, null);
    }
    // For a text file the application can check every account id against the words themselves.
    String visible = document.isText() ? document.text() + "\n" + cleanNote : null;
    return resolve(extractor, read, visible, name, true);
  }

  /**
   * @param visibleText the text the person gave, to check account ids against; null when the
   *     application cannot read the document itself (PDF, image)
   */
  private SentenceInterpretation resolve(
      IntentExtractor extractor, ExtractedIntent read, String visibleText, String document,
      boolean fromDocument) {
    PaymentIntent intent =
        new PaymentIntent(
            blank(read.recipient()),
            blank(read.amount()),
            blank(read.asset()),
            blank(read.envelope()),
            blank(read.memo()),
            blank(read.keepAtLeast()));
    String clarification = blank(read.clarification());
    String what = fromDocument ? "document" : "sentence";

    if (!read.isPayment()) {
      return new SentenceInterpretation(true,
          "This " + what + " does not read as a payment request.", extractor.source(), intent,
          clarification, null, document, null);
    }

    String warning = null;
    String recipient = intent.recipient();
    if (recipient != null && recipient.matches(CreatePaymentRequest.ACCOUNT_ID)) {
      if (visibleText != null && !visibleText.contains(recipient)) {
        // The model may only repeat an account id the person gave; it may never supply one.
        return new SentenceInterpretation(true,
            "Refused: the language model proposed account " + recipient
                + ", which is not in your " + what
                + ". Recipients come from your words or your contacts.",
            extractor.source(), null, null, null, document, null);
      }
      if (visibleText == null && !isContact(recipient)) {
        warning = "Account " + recipient + " was read from " + document
            + " and is not one of your contacts. Compare it with the document, character by"
            + " character, before you confirm.";
      }
    }

    IntentUnderstanding understanding = intents.understand(intent);
    return new SentenceInterpretation(true,
        understanding.understood() ? "Understood: check it, then preview." : "Not fully understood.",
        extractor.source(), intent, clarification, understanding, document, warning);
  }

  private boolean isContact(String accountId) {
    return contacts.list().stream().anyMatch(c -> accountId.equals(c.accountId));
  }

  static boolean isSupported(String type) {
    return DocumentPrompt.TEXT_TYPES.contains(type)
        || DocumentPrompt.PDF.equals(type)
        || DocumentPrompt.IMAGE_TYPES.contains(type);
  }

  /** The declared type must match the bytes: a renamed executable is not a PDF. */
  static boolean contentMatches(String type, byte[] data) {
    return switch (type) {
      case DocumentPrompt.PDF -> startsWith(data, "%PDF-".getBytes(StandardCharsets.US_ASCII));
      case "image/png" -> startsWith(data, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
      case "image/jpeg" -> startsWith(data, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
      case "image/webp" ->
          data.length > 12
              && startsWith(data, "RIFF".getBytes(StandardCharsets.US_ASCII))
              && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
      default -> isUtf8Text(data);
    };
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
  }

  private static boolean isUtf8Text(byte[] data) {
    try {
      String text =
          StandardCharsets.UTF_8.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(data))
              .toString();
      return text.chars().noneMatch(c -> c == 0);
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  /** A file name is shown back and given to the model: no paths, no control characters. */
  private static String cleanName(String fileName) {
    String name = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "");
    name = name.replaceAll("[\\p{Cntrl}\"<>]", "").trim();
    if (name.isEmpty()) {
      name = "document";
    }
    return name.length() > 120 ? name.substring(0, 120) : name;
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
