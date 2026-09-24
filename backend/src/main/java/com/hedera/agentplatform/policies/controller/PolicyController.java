package com.hedera.agentplatform.policies.controller;
import com.hedera.agentplatform.policies.PolicyEngine;
import com.hedera.agentplatform.policies.PolicyRequest;
import com.hedera.agentplatform.policies.PolicyState;
import com.hedera.agentplatform.policies.dto.*;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import com.hedera.agentplatform.policies.service.PolicyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

  private final PolicyService service;
  private final ApprovalService approvals;
  private final EnvelopeLedger ledger;

  public PolicyController(PolicyService service, ApprovalService approvals, EnvelopeLedger ledger) {
    this.service = service;
    this.approvals = approvals;
    this.ledger = ledger;
  }

  @GetMapping
  public List<RuleResponse> rulebook() {
    return service.rulebook();
  }

  @GetMapping("/state")
  public StateResponse state() {
    PolicyState current = ledger.state();
    Map<String, Long> envelopes = new LinkedHashMap<>();
    for (PolicyEngine.Envelope envelope : PolicyEngine.Envelope.values()) {
      envelopes.put(envelope.name(), current.balances().get(envelope));
    }
    return new StateResponse(envelopes, current.knownCounterparties());
  }

  /** Puts the demo back to its opening position so it can be run again. Audit history is kept. */
  @PostMapping("/demo/reset")
  public StateResponse resetDemo() {
    ledger.reset();
    return state();
  }

  @PostMapping("/decide")
  public DecideResponse decide(@RequestBody DecideRequest body) {
    ApprovalService.Submission submission =
        approvals.submit(
            new PolicyRequest(envelopeOf(body.envelope()), body.amount(), body.counterparty()),
            ledger.state());
    return new DecideResponse(
        submission.decision().verdict().name(),
        submission.decision().ruleId(),
        submission.decision().reason(),
        submission.decision().balanceAfter(),
        submission.approval() == null ? null : submission.approval().id(),
        submission.auditEventId(),
        submission.anchored(),
        state().envelopes());
  }

  private static PolicyEngine.Envelope envelopeOf(String name) {
    for (PolicyEngine.Envelope envelope : PolicyEngine.Envelope.values()) {
      if (envelope.name().equalsIgnoreCase(name)) {
        return envelope;
      }
    }
    return null;
  }

  @GetMapping("/approvals")
  public List<ApprovalResponse> approvals() {
    return service.approvals();
  }

  @PostMapping("/approvals/{id}/approve")
  public ResponseEntity<?> approve(@PathVariable String id) {
    try {
      return ResponseEntity.ok(approvals.approve(id));
    } catch (IllegalArgumentException e) {
      return refusal(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      return refusal(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  @PostMapping("/approvals/{id}/reject")
  public ResponseEntity<?> reject(@PathVariable String id) {
    try {
      return ResponseEntity.ok(approvals.reject(id));
    } catch (IllegalArgumentException e) {
      return refusal(HttpStatus.NOT_FOUND, e.getMessage());
    } catch (IllegalStateException e) {
      return refusal(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  /** A refusal a human can read: the rule that stopped them, not a bare status code. */
  private static ResponseEntity<Map<String, String>> refusal(HttpStatus status, String reason) {
    return ResponseEntity.status(status).body(Map.of("error", reason));
  }
}
