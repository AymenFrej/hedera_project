package com.hedera.agentplatform.policies;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import java.util.List;
import java.util.Map;

/** Remaining balance per envelope, plus counterparties already transferred to before. */
public record PolicyState(Map<Envelope, Long> balances, List<String> knownCounterparties) {}
