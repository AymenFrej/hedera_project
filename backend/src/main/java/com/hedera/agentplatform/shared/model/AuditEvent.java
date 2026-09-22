package com.hedera.agentplatform.shared.model;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(String id, String agent, String action, String status, Instant createdAt, Map<String, Object> metadata) {}
