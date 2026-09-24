package com.hedera.agentplatform.policies;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;

/**
 * A spend the agent wants to make.
 *
 * <p>{@code envelopeName} is what the caller actually wrote. It is kept alongside the resolved
 * {@code envelope} so a refusal can quote a name the engine did not recognise instead of claiming
 * none was given.
 *
 * <p>{@code asset} is what is being moved. Envelopes are a budget in HBAR, held in tinybars, so
 * only an HBAR spend can be counted against one: a token has its own supply and its own decimals,
 * and 2 BEAN is not 200 tinybars. A token payment is still judged — an unknown counterparty is held
 * for a human whatever the asset — but it does not debit an envelope.
 */
public record PolicyRequest(
    Envelope envelope, String envelopeName, long amount, String counterparty, String asset) {

  /** The native asset, the only one an envelope can be denominated in. */
  public static final String HBAR = "HBAR";

  public PolicyRequest(Envelope envelope, long amount, String counterparty) {
    this(envelope, envelope == null ? null : envelope.name(), amount, counterparty, HBAR);
  }

  public PolicyRequest(Envelope envelope, String envelopeName, long amount, String counterparty) {
    this(envelope, envelopeName, amount, counterparty, HBAR);
  }

  /** A spend in something other than HBAR: judged on its counterparty, charged to no envelope. */
  public static PolicyRequest ofToken(
      Envelope envelope, long amount, String counterparty, String asset) {
    return new PolicyRequest(
        envelope, envelope == null ? null : envelope.name(), amount, counterparty, asset);
  }

  /** Whether this spend is denominated in the unit the envelopes are held in. */
  public boolean spendsEnvelope() {
    return asset == null || HBAR.equalsIgnoreCase(asset);
  }
}
