package com.hedera.agentplatform.audit.hedera;
import com.hedera.agentplatform.shared.model.AuditEvent;
import org.springframework.stereotype.Component;
@Component public class MockHederaAuditGateway implements HederaAuditGateway { public void publish(AuditEvent event) { /* HCS integration placeholder */ } }
