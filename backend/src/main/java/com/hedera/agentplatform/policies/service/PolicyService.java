package com.hedera.agentplatform.policies.service;
import com.hedera.agentplatform.policies.dto.*;
import java.util.List;
import org.springframework.stereotype.Service;
@Service public class PolicyService { public List<PolicyResponse> findAll() { return List.of(new PolicyResponse("policy_demo", "Starter approval policy", "Placeholder for spending rules", "DRAFT")); } public List<ApprovalResponse> approvals() { return List.of(new ApprovalResponse("approval_demo", "task_demo", "PENDING")); } }
