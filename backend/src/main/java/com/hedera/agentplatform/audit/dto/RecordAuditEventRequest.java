package com.hedera.agentplatform.audit.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Body of POST /api/v1/audit.
 *
 * <p>Deliberately carries no actor field: the actor is resolved server-side from the session. If a
 * client could declare who performed an action, the trail would prove nothing.
 */
public record RecordAuditEventRequest(
        @NotBlank String agent,
        @NotBlank String action,
        @NotBlank String status,
        Map<String, Object> metadata) {}
