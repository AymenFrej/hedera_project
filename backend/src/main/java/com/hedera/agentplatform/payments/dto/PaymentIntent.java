package com.hedera.agentplatform.payments.dto;

/**
 * What someone wants to pay, as they would say it, before anything is resolved.
 *
 * <p>This is the contract the future orchestrator produces from a sentence ("Pay Zied 5 PAYTEST
 * from essentials, keep at least 10"). Today the Payments page produces it from a form. Either way
 * it goes through the same resolution, preview, policy and audit: nothing here is trusted as is.
 *
 * @param recipient a contact name ("Zied") or a Hedera account id
 * @param asset "HBAR", a token symbol held by the paying account ("PAYTEST"), or a token id
 * @param keepAtLeast the requester's own condition on the remaining balance of the asset; optional
 */
public record PaymentIntent(
    String recipient,
    String amount,
    String asset,
    String envelope,
    String memo,
    String keepAtLeast) {}
