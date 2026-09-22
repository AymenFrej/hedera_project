package com.hedera.agentplatform.shared.model;

import java.time.Instant;

public record AgentTask(String taskId, String agent, AgentStatus status, Instant createdAt) {}
