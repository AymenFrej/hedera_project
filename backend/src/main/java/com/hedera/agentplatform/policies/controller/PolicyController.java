package com.hedera.agentplatform.policies.controller;
import com.hedera.agentplatform.policies.dto.*;
import com.hedera.agentplatform.policies.service.PolicyService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/policies") public class PolicyController { private final PolicyService service; public PolicyController(PolicyService service) { this.service = service; } @GetMapping public List<PolicyResponse> findAll() { return service.findAll(); } @GetMapping("/approvals") public List<ApprovalResponse> approvals() { return service.approvals(); } }
