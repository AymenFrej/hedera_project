package com.hedera.agentplatform.audit.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Body of POST /api/v1/audit. */
public record RecordAuditEventRequest(
    @NotBlank String agent,
    @NotBlank String action,
    @NotBlank String status,
    Map<String, Object> metadata) {}
