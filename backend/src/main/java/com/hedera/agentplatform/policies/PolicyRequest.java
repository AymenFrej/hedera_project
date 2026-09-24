package com.hedera.agentplatform.policies;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;

/**
 * A spend the agent wants to make.
 *
 * <p>{@code envelopeName} is what the caller actually wrote. It is kept alongside the resolved
 * {@code envelope} so a refusal can quote a name the engine did not recognise instead of claiming
 * none was given.
 */
public record PolicyRequest(
    Envelope envelope, String envelopeName, long amount, String counterparty) {

  public PolicyRequest(Envelope envelope, long amount, String counterparty) {
    this(envelope, envelope == null ? null : envelope.name(), amount, counterparty);
  }
}
