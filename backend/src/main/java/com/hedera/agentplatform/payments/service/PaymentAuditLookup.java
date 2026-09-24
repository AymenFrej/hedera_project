package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Finds the audit events of one payment, whoever wrote them.
 *
 * <ul>
 *   <li>Payments' own events carry {@code "paymentId":"<id>"} in their payload, the exact bytes
 *       anchored to HCS.
 *   <li>The Policies module records the decision as its own event, whose id the payment stores.
 *   <li>A reviewer's answer is recorded by the Policies module with {@code "approvalId":"<id>"}.
 * </ul>
 *
 * <p>Read-only: the audit module stays the owner of its data.
 */
@Component
class PaymentAuditLookup {

  /** Agent name the Policies module records a reviewer's answer under. */
  static final String APPROVER = "HumanApprover";

  private final EntityManager entityManager;

  PaymentAuditLookup(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  List<AuditEventEntity> eventsFor(String paymentId, String policyAuditEventId, String approvalId) {
    Map<String, AuditEventEntity> events = new LinkedHashMap<>();
    for (AuditEventEntity e : byMarker(PaymentService.AGENT, "paymentId", paymentId)) {
      events.put(e.id, e);
    }
    if (policyAuditEventId != null) {
      AuditEventEntity decision = entityManager.find(AuditEventEntity.class, policyAuditEventId);
      if (decision != null) {
        events.put(decision.id, decision);
      }
    }
    if (approvalId != null) {
      for (AuditEventEntity e : byMarker(APPROVER, "approvalId", approvalId)) {
        events.put(e.id, e);
      }
    }
    return events.values().stream()
        .sorted(Comparator.comparing((AuditEventEntity e) -> e.createdAt))
        .toList();
  }

  private List<AuditEventEntity> byMarker(String agent, String key, String value) {
    if (value == null) {
      return List.of();
    }
    String marker = "\"" + key + "\":\"" + value + "\"";
    return entityManager
        .createQuery(
            "select e from AuditEventEntity e where e.agent = :agent"
                + " and e.payload like :marker escape '!'",
            AuditEventEntity.class)
        .setParameter("agent", agent)
        .setParameter("marker", "%" + escapeLike(marker) + "%")
        .getResultList()
        .stream()
        // Exact check on top of LIKE, which is only a coarse filter.
        .filter(e -> e.payload != null && e.payload.contains(marker))
        .toList();
  }

  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
