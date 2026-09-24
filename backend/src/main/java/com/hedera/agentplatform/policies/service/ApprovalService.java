package com.hedera.agentplatform.policies.service;

import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyEngine;
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
import java.util.List;
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
  private final EnvelopeLedger ledger;

  public ApprovalService(
      PolicyDecisionService decisions,
      ApprovalRepository repository,
      AuditService auditService,
      ActorResolver actorResolver,
      EnvelopeLedger ledger) {
    this.decisions = decisions;
    this.repository = repository;
    this.auditService = auditService;
    this.actorResolver = actorResolver;
    this.ledger = ledger;
  }

  /**
   * A decision plus the approval it created.
   *
   * @param approval null for ALLOW and DENY, which need no human
   */
  public record Submission(
      PolicyDecision decision, ApprovalResponse approval, String auditEventId, boolean anchored) {}

  public Submission submit(PolicyRequest request, PolicyState state) {
    PolicyDecisionService.RecordedDecision recorded = decisions.decide(request, state);
    PolicyDecision decision = recorded.decision();

    if (decision.verdict() != Verdict.HOLD) {
      // Only an HBAR spend moves an envelope: a token's units are not tinybars.
      if (decision.verdict() == Verdict.ALLOW && request.spendsEnvelope()) {
        ledger.debit(request.envelope(), request.amount());
      }
      return new Submission(decision, null, recorded.auditEvent().id, recorded.anchored());
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
    entity.asset = request.asset();

    return new Submission(
        decision, toResponse(repository.save(entity)), recorded.auditEvent().id, recorded.anchored());
  }

  /** The questions still waiting for a human. */
  public List<ApprovalResponse> pending() {
    return repository.findByStatus("PENDING").stream().map(ApprovalService::toResponse).toList();
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

    // Only an HBAR approval touches an envelope. A token amount is in the token's own units, so
    // checking it against tinybars would refuse a payment the envelope has nothing to say about.
    boolean spendsEnvelope =
        entity.asset == null || PolicyRequest.HBAR.equalsIgnoreCase(entity.asset);

    if ("APPROVED".equals(status) && spendsEnvelope) {
      PolicyEngine.Envelope envelope = PolicyEngine.Envelope.valueOf(entity.envelope);
      long available = ledger.state().balances().getOrDefault(envelope, 0L);
      if (entity.amount > available) {
        throw new IllegalStateException(
            "Approval "
                + approvalId
                + " is no longer affordable: "
                + entity.envelope
                + " holds "
                + available
                + " but the request is for "
                + entity.amount);
      }
    }

    Actor actor = actorResolver.currentActor();
    entity.status = status;
    entity.decidedAt = Instant.now();
    entity.decidedBy = actor == null ? null : actor.id();

    if ("APPROVED".equals(status) && spendsEnvelope) {
      ledger.debit(PolicyEngine.Envelope.valueOf(entity.envelope), entity.amount);
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("approvalId", entity.id);
    metadata.put("ruleId", entity.ruleId);
    metadata.put("envelope", entity.envelope);
    metadata.put("amount", entity.amount);
    metadata.put("counterparty", entity.counterparty);
    metadata.put("asset", entity.asset);
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
