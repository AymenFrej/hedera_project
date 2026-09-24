package com.hedera.agentplatform.payments.language;

import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;

/**
 * What became of a sentence: what the language model read in it, and then what the application
 * resolved from that, with sources.
 *
 * @param available false when no language model is configured; {@code detail} says so
 * @param source the model that read the sentence
 * @param intent the fields the model read; null when it could not read the sentence
 * @param clarification what the model found missing or unclear; null when nothing
 * @param understanding the application's resolution of {@code intent} (contacts, Mirror Node);
 *     null when there was nothing to resolve
 */
public record SentenceInterpretation(
    boolean available,
    String detail,
    String source,
    PaymentIntent intent,
    String clarification,
    IntentUnderstanding understanding) {}
