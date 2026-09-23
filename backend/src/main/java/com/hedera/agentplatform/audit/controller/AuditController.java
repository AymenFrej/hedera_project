package com.hedera.agentplatform.audit.controller;

import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.audit.dto.RecordAuditEventRequest;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.audit.service.AuditService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

  private final AuditService service;

  public AuditController(AuditService service) {
    this.service = service;
  }

  @GetMapping
  public List<AuditEventResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public AuditEventResponse findById(@PathVariable String id) {
    return service.findById(id);
  }

  /** Records an agent action and anchors it to HCS. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AuditEventResponse record(@Valid @RequestBody RecordAuditEventRequest request) {
    AuditEventEntity entity =
        service.record(request.agent(), request.action(), request.status(), request.metadata());
    return service.findById(entity.id);
  }

  /** Reads the event back from the Mirror Node and compares it with what we stored. */
  @GetMapping("/{id}/verification")
  public VerificationResult verify(@PathVariable String id) {
    return service.verify(id);
  }

  /** Tells the UI whether events are really hitting the ledger. */
  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("ledgerActive", service.isLedgerActive());
  }
}
