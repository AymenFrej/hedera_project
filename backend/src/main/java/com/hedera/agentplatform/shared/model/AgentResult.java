package com.hedera.agentplatform.shared.model;

import java.util.Map;

public record AgentResult(String taskId, AgentStatus status, String message, Map<String, Object> data) {}
