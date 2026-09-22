package com.hedera.agentplatform.shared.model;

import java.util.Map;

public record AgentRequest(String requestId, AgentIntent intent, String prompt, Map<String, Object> context) {}
