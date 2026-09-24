package com.hedera.agentplatform.policies.service;
import com.hedera.agentplatform.policies.dto.*;
import com.hedera.agentplatform.policies.repository.ApprovalRepository;
import java.util.List;
import org.springframework.stereotype.Service;
@Service public class PolicyService { private final ApprovalRepository approvalRepository; public PolicyService(ApprovalRepository approvalRepository) { this.approvalRepository = approvalRepository; } public List<PolicyResponse> findAll() { return List.of(new PolicyResponse("policy_demo", "Starter approval policy", "Placeholder for spending rules", "DRAFT")); } public List<ApprovalResponse> approvals() { return approvalRepository.findAllByOrderByRequestedAtDesc().stream().map(ApprovalService::toResponse).toList(); } }
