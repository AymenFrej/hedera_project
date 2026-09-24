package com.hedera.agentplatform.policies.service;
import com.hedera.agentplatform.policies.Rulebook;
import com.hedera.agentplatform.policies.dto.*;
import com.hedera.agentplatform.policies.repository.ApprovalRepository;
import java.util.List;
import org.springframework.stereotype.Service;
@Service public class PolicyService { private final ApprovalRepository approvalRepository; public PolicyService(ApprovalRepository approvalRepository) { this.approvalRepository = approvalRepository; }
  /** The rules the engine applies, readable before a spend instead of inferred from a verdict after one. */
  public List<RuleResponse> rulebook() { return Rulebook.rules().stream().map(rule -> new RuleResponse(rule.ruleId(), rule.verdict().name(), rule.reason())).toList(); }
  public List<ApprovalResponse> approvals() { return approvalRepository.findAllByOrderByRequestedAtDesc().stream().map(ApprovalService::toResponse).toList(); } }
