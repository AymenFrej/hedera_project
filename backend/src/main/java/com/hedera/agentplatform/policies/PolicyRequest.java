package com.hedera.agentplatform.policies;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;

/** A spend the agent wants to make. */
public record PolicyRequest(Envelope envelope, long amount, String counterparty) {}
