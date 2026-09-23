package com.hedera.agentplatform.policies.service;

import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyRequest;
import com.hedera.agentplatform.policies.PolicyState;
import com.hedera.agentplatform.policies.Verdict;
import com.hedera.agentplatform.policies.dto.ApprovalResponse;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import com.hedera.agentplatform.policies.repository.ApprovalRepository;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Turns a HOLD into a question a human can answer.
 *
 * <p>Only HOLD creates a request. ALLOW needs nobody, and DENY must never reach a human: offering
 * an unsettleable spend for approval is how a refusal gets talked around.
 */
@Service
public class ApprovalService {

  private final PolicyDecisionService decisions;
  private final ApprovalRepository repository;
  private final AuditService auditService;
  private final ActorResolver actorResolver;

  public ApprovalService(
      PolicyDecisionService decisions,
      ApprovalRepository repository,
      AuditService auditService,
      ActorResolver actorResolver) {
    this.decisions = decisions;
    this.repository = repository;
    this.auditService = auditService;
    this.actorResolver = actorResolver;
  }

  /**
   * A decision plus the approval it created.
   *
   * @param approval null for ALLOW and DENY, which need no human
   */
  public record Submission(PolicyDecision decision, ApprovalResponse approval) {}

  public Submission submit(PolicyRequest request, PolicyState state) {
    PolicyDecisionService.RecordedDecision recorded = decisions.decide(request, state);
    PolicyDecision decision = recorded.decision();

    if (decision.verdict() != Verdict.HOLD) {
      return new Submission(decision, null);
    }

    ApprovalEntity entity = new ApprovalEntity();
    entity.id = "approval_" + UUID.randomUUID();
    entity.taskId = recorded.auditEvent().id;
    entity.status = "PENDING";
    entity.requestedAt = Instant.now();
    entity.ruleId = decision.ruleId();
    entity.reason = decision.reason();
    entity.envelope = request.envelope() == null ? null : request.envelope().name();
    entity.amount = request.amount();
    entity.counterparty = request.counterparty();

    return new Submission(decision, toResponse(repository.save(entity)));
  }

  /** Records a human's approval of a pending request. The answer is itself an audit event. */
  public ApprovalResponse approve(String approvalId) {
    return answer(approvalId, "APPROVED");
  }

  /** Records a human's refusal. A refusal is recorded exactly like an approval. */
  public ApprovalResponse reject(String approvalId) {
    return answer(approvalId, "REJECTED");
  }

  private ApprovalResponse answer(String approvalId, String status) {
    ApprovalEntity entity =
        repository
            .findById(approvalId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown approval: " + approvalId));

    if (!"PENDING".equals(entity.status)) {
      throw new IllegalStateException(
          "Approval " + approvalId + " was already " + entity.status.toLowerCase(Locale.ROOT));
    }

    Actor actor = actorResolver.currentActor();
    entity.status = status;
    entity.decidedAt = Instant.now();
    entity.decidedBy = actor == null ? null : actor.id();

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("approvalId", entity.id);
    metadata.put("ruleId", entity.ruleId);
    metadata.put("envelope", entity.envelope);
    metadata.put("amount", entity.amount);
    metadata.put("counterparty", entity.counterparty);
    metadata.put("decisionAuditEventId", entity.taskId);

    auditService.record("HumanApprover", "POLICY_APPROVAL", status, metadata);

    return toResponse(repository.save(entity));
  }

  static ApprovalResponse toResponse(ApprovalEntity entity) {
    return new ApprovalResponse(
        entity.id,
        entity.taskId,
        entity.status,
        entity.ruleId,
        entity.reason,
        entity.envelope,
        entity.amount,
        entity.counterparty,
        entity.requestedAt,
        entity.decidedAt,
        entity.decidedBy);
  }
}
