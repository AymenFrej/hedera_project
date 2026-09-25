package com.hedera.agentplatform.tokens.dto;

/**
 * A token as the person describes it in the Token Studio (or as the Token Agent read it from a
 * sentence), before validation. Everything is text so nothing is lost or rounded on the way in.
 *
 * @param supplyPolicy FIXED (no one can ever mint more), CAPPED (mintable up to maxSupply) or
 *     UNLIMITED (mintable without a cap)
 * @param initialSupply in whole tokens, e.g. "1000000" or "12.5"
 * @param maxSupply in whole tokens; only for CAPPED
 */
public record TokenDraft(
    String name,
    String symbol,
    String decimals,
    String initialSupply,
    String supplyPolicy,
    String maxSupply,
    String memo) {}
