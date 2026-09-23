package com.hedera.agentplatform.policies.controller;
import com.hedera.agentplatform.policies.dto.*;
import com.hedera.agentplatform.policies.service.ApprovalService;
import com.hedera.agentplatform.policies.service.PolicyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

  private final PolicyService service;
  private final ApprovalService approvals;

  public PolicyController(PolicyService service, ApprovalService approvals) {
    this.service = service;
    this.approvals = approvals;
  }

  @GetMapping
  public List<PolicyResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/approvals")
  public List<ApprovalResponse> approvals() {
    return service.approvals();
  }

  @PostMapping("/approvals/{id}/approve")
  public ApprovalResponse approve(@PathVariable String id) {
    try {
      return approvals.approve(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping("/approvals/{id}/reject")
  public ApprovalResponse reject(@PathVariable String id) {
    try {
      return approvals.reject(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }
}
