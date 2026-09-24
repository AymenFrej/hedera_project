package com.hedera.agentplatform.policies.service;

import com.hedera.agentplatform.audit.entity.AnchorStatus;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyEngine;
import com.hedera.agentplatform.policies.PolicyRequest;
import com.hedera.agentplatform.policies.PolicyState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Decides, then writes the decision to the audit trail.
 *
 * <p>The decision itself stays a pure function in {@link PolicyEngine}. What this adds is the
 * record: a verdict nobody can read back later is an opinion, not governance. DENY is recorded
 * exactly like ALLOW — a refusal is the most interesting thing an agent does.
 *
 * <p>The actor is never taken from the request; {@link AuditService} resolves it server-side.
 */
@Service
public class PolicyDecisionService {

  private static final String AGENT = "PolicyAgent";

  private final AuditService auditService;

  public PolicyDecisionService(AuditService auditService) {
    this.auditService = auditService;
  }

  /**
   * The decision plus its audit record.
   *
   * @param anchored false when the event is not on the ledger, so a caller can show that instead of
   *     implying a proof that does not exist
   */
  public record RecordedDecision(
      PolicyDecision decision, AuditEventEntity auditEvent, boolean anchored) {}

  public RecordedDecision decide(PolicyRequest request, PolicyState state) {
    PolicyDecision decision = PolicyEngine.decide(request, state);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("ruleId", decision.ruleId());
    metadata.put("reason", decision.reason());
    metadata.put("envelope", request.envelope() == null ? null : request.envelope().name());
    metadata.put("amount", request.amount());
    metadata.put("counterparty", request.counterparty());
    metadata.put("balanceAfter", decision.balanceAfter());

    AuditEventEntity event =
        auditService.record(AGENT, "POLICY_DECISION", decision.verdict().name(), metadata);

    return new RecordedDecision(
        decision, event, AnchorStatus.ANCHORED.name().equals(event.anchorStatus));
  }
}
