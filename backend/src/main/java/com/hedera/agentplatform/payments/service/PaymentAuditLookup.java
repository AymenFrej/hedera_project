package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Finds the audit events Payments wrote for one payment.
 *
 * <p>Every such event carries {@code "paymentId":"<id>"} in its payload, the exact bytes anchored to
 * HCS, so the link is read from what was actually recorded rather than from a separate table that
 * could disagree with it. Read-only: the audit module stays the owner of its data.
 */
@Component
class PaymentAuditLookup {

  private final EntityManager entityManager;

  PaymentAuditLookup(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  List<AuditEventEntity> eventsFor(String paymentId) {
    String marker = "\"paymentId\":\"" + paymentId + "\"";
    List<AuditEventEntity> candidates =
        entityManager
            .createQuery(
                "select e from AuditEventEntity e where e.agent = :agent"
                    + " and e.payload like :marker escape '!'",
                AuditEventEntity.class)
            .setParameter("agent", PaymentService.AGENT)
            .setParameter("marker", "%" + escapeLike(marker) + "%")
            .getResultList();
    return candidates.stream()
        // Exact check on top of LIKE, which is only a coarse filter.
        .filter(e -> e.payload != null && e.payload.contains(marker))
        .sorted(Comparator.comparing((AuditEventEntity e) -> e.createdAt))
        .toList();
  }

  private static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
