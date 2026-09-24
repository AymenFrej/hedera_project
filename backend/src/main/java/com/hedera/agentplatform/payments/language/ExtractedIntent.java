package com.hedera.agentplatform.payments.language;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What the language model is allowed to return: the fields of a payment request as the person said
 * them, and nothing else. No account id is ever derived here; a name stays a name and is resolved
 * later from trusted application data (the contacts).
 *
 * <p>Empty string means "not said". The schema is sent to the model as a structured-output format,
 * so the response is this shape or the request fails.
 */
public record ExtractedIntent(
    @JsonPropertyDescription(
            "true only if the text asks to pay, send or transfer money or tokens to someone")
        boolean isPayment,
    @JsonPropertyDescription(
            "the recipient exactly as written: a person's name (e.g. Zied) or a Hedera account id"
                + " (e.g. 0.0.12345) if one is written. Never invent or guess an account id. Empty"
                + " if not said.")
        String recipient,
    @JsonPropertyDescription("the amount as plain decimal digits, e.g. 500 or 1.5. Empty if not said.")
        String amount,
    @JsonPropertyDescription(
            "the asset as written: HBAR, a token symbol (e.g. USDC, PAYTEST) or a token id. Empty"
                + " if not said.")
        String asset,
    @JsonPropertyDescription(
            "the budget envelope if named: rent, essentials or emergency. Empty otherwise.")
        String envelope,
    @JsonPropertyDescription(
            "if the person sets a minimum to keep (e.g. 'keep at least 100 HBAR'), that minimum as"
                + " plain decimal digits. Empty otherwise.")
        String keepAtLeast,
    @JsonPropertyDescription("a short note for the transfer if the person gives one. Empty otherwise.")
        String memo,
    @JsonPropertyDescription(
            "if something needed is missing, ambiguous or not a payment, one short sentence saying"
                + " what. Empty if the request is clear.")
        String clarification) {}
