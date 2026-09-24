package com.hedera.agentplatform.policies.dto;

import java.util.List;
import java.util.Map;

/** The demo budget the engine decides against, so the console shows the same numbers. */
public record StateResponse(Map<String, Long> envelopes, List<String> knownCounterparties) {}
