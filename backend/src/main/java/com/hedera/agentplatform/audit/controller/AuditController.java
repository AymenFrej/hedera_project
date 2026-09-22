package com.hedera.agentplatform.audit.controller;
import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.audit.service.AuditService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/audit") public class AuditController { private final AuditService service; public AuditController(AuditService service) { this.service = service; } @GetMapping public List<AuditEventResponse> findAll() { return service.findAll(); } }
