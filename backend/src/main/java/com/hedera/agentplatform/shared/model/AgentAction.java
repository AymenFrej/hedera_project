package com.hedera.agentplatform.shared.model;

import java.util.Map;

public record AgentAction(String type, String description, Map<String, Object> parameters) {}
