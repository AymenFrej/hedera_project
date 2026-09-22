package com.hedera.agentplatform.audit.hedera;
import com.hedera.agentplatform.shared.model.AuditEvent;
public interface HederaAuditGateway { void publish(AuditEvent event); }
