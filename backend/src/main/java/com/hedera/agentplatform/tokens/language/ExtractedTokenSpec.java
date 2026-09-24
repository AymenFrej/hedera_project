package com.hedera.agentplatform.tokens.language;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What the language model may return about a token: the fields of the Token Studio as the person
 * said them, nothing else. Empty string means "not said": the Studio then asks, it never guesses.
 */
public record ExtractedTokenSpec(
    @JsonPropertyDescription("true only if the text asks to create or design a new token")
        boolean isTokenRequest,
    @JsonPropertyDescription("the token's name as written, e.g. Coffee Beans. Empty if not said.")
        String name,
    @JsonPropertyDescription(
            "the symbol as written, e.g. BEAN. Empty if not said; never invent one from the name.")
        String symbol,
    @JsonPropertyDescription(
            "decimals as plain digits (0 means whole units only, e.g. 'no decimals' is 0, 'cents'"
                + " is 2). Empty if not said.")
        String decimals,
    @JsonPropertyDescription(
            "the initial supply in whole tokens as plain digits: '1 million' becomes 1000000."
                + " Empty if not said.")
        String initialSupply,
    @JsonPropertyDescription(
            "FIXED if the supply must never grow ('fixed', 'no more ever'), CAPPED if more may be"
                + " minted up to a maximum, UNLIMITED if more may be minted without a cap. Empty if"
                + " not said.")
        String supplyPolicy,
    @JsonPropertyDescription("the maximum supply in whole tokens as plain digits, only for CAPPED. Empty otherwise.")
        String maxSupply,
    @JsonPropertyDescription("a short description or memo if one is given, at most 100 characters. Empty otherwise.")
        String memo,
    @JsonPropertyDescription(
            "if something needed is missing or unclear, or the text is not about creating a token,"
                + " one short sentence saying what. Empty if the request is clear.")
        String clarification) {}
