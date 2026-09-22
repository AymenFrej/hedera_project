package com.hedera.agentplatform.audit.service;
import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import java.util.List;
import org.springframework.stereotype.Service;
@Service public class AuditService { public List<AuditEventResponse> findAll() { return List.of(new AuditEventResponse("audit_demo", "PaymentAgent", "TRANSFER", "MOCK")); } }
