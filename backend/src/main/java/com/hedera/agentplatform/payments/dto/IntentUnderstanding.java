package com.hedera.agentplatform.payments.dto;

import java.util.List;

/**
 * An intent, resolved field by field, with where each value came from.
 *
 * @param understood true when every field resolved; {@code request} is then ready for preview
 * @param request the payment the intent describes; null when something did not resolve
 * @param steps each resolution, in order, with its source
 * @param problems what could not be resolved, in words; empty when understood
 * @param envelopeChoices the envelopes the person can pick from when none was said; empty
 *     otherwise. The policy denies a request without an envelope, so it is asked for here.
 */
public record IntentUnderstanding(
    boolean understood,
    CreatePaymentRequest request,
    String recipientName,
    String assetSymbol,
    List<Resolution> steps,
    List<String> problems,
    List<String> envelopeChoices) {

  /**
   * @param field what was resolved, e.g. "Recipient"
   * @param input what was asked, e.g. "Zied"
   * @param value what it resolved to, e.g. "0.0.10682427"; null when it did not resolve
   * @param source where the value came from, e.g. "your contacts", "Mirror Node"
   */
  public record Resolution(String field, String input, String value, String source) {}
}
