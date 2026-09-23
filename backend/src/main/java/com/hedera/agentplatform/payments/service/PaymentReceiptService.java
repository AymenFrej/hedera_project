package com.hedera.agentplatform.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.audit.entity.AnchorStatus;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.payments.dto.PaymentReceipt;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.AuditProof;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.Badges;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.Step;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the result screen of a payment from what actually happened: the payment record, the
 * Mirror Node's view of its transaction, and the audit events it wrote, each verified on HCS.
 *
 * <p>Nothing here is a fixed script of steps. A step appears because its audit event exists, and a
 * badge appears because the corresponding check just passed.
 */
@Service
public class PaymentReceiptService {

  private static final Logger log = LoggerFactory.getLogger(PaymentReceiptService.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final PaymentService payments;
  private final PaymentAuditLookup auditLookup;
  private final AuditService auditService;
  private final HederaProperties properties;

  public PaymentReceiptService(
      PaymentService payments,
      PaymentAuditLookup auditLookup,
      AuditService auditService,
      HederaProperties properties) {
    this.payments = payments;
    this.auditLookup = auditLookup;
    this.auditService = auditService;
    this.properties = properties;
  }

  public PaymentReceipt receipt(String paymentId) {
    PaymentResponse before = payments.findById(paymentId);

    // Verification may settle a payment left SUBMITTED, so read the payment again afterwards.
    PaymentVerification ledger =
        before.transactionId() != null && payments.isLedgerActive()
            ? payments.verify(paymentId)
            : null;
    PaymentResponse payment = ledger == null ? before : payments.findById(paymentId);

    List<AuditEventEntity> events = auditLookup.eventsFor(paymentId);
    List<AuditProof> audit = events.stream().map(this::prove).toList();

    boolean onLedger = ledger != null && ledger.ledgerResult() != null;
    boolean auditVerified =
        !audit.isEmpty() && audit.stream().allMatch(a -> Boolean.TRUE.equals(a.verified()));
    Badges badges =
        new Badges(
            payment.policyRuleId() != null && !"policy.none".equals(payment.policyRuleId()),
            ledger != null && ledger.verified() && "CONFIRMED".equals(payment.status()),
            auditVerified);

    String outcome = outcome(payment);
    return new PaymentReceipt(
        payment,
        outcome,
        headline(outcome),
        detail(outcome, payment),
        onLedger,
        ledger,
        timeline(payment, events),
        audit,
        badges,
        properties.getNetwork());
  }

  /** The audit events of one payment, as the audit module reports them. */
  public List<AuditEventResponse> auditEvents(String paymentId) {
    payments.findById(paymentId);
    return auditLookup.eventsFor(paymentId).stream()
        .map(e -> auditService.findById(e.id))
        .toList();
  }

  /** Reads one audit event of this payment back from HCS, through the audit module. */
  public VerificationResult verifyAuditEvent(String paymentId, String eventId) {
    boolean belongs =
        auditLookup.eventsFor(paymentId).stream().anyMatch(e -> e.id.equals(eventId));
    if (!belongs) {
      throw new IllegalArgumentException(
          "Audit event " + eventId + " does not belong to payment " + paymentId);
    }
    return auditService.verify(eventId);
  }

  private AuditProof prove(AuditEventEntity e) {
    Boolean verified = null;
    String detail;
    String explorerUrl = null;
    if (AnchorStatus.ANCHORED.name().equals(e.anchorStatus)) {
      try {
        VerificationResult v = auditService.verify(e.id);
        verified = v.verified();
        detail = v.detail();
        explorerUrl = v.explorerUrl();
      } catch (RuntimeException ex) {
        log.warn("Could not verify audit event {}: {}", e.id, ex.getMessage());
        verified = false;
        detail = "Could not verify: " + ex.getMessage();
      }
    } else if (AnchorStatus.FAILED.name().equals(e.anchorStatus)) {
      detail = "Recorded locally; submitting it to HCS failed";
    } else {
      detail = "Recorded locally only: not written to HCS (no Hedera credentials)";
    }
    return new AuditProof(
        e.id,
        e.action,
        e.status,
        e.createdAt,
        e.anchorStatus,
        e.topicId,
        e.sequenceNumber,
        verified,
        detail,
        explorerUrl);
  }

  private static String outcome(PaymentResponse p) {
    return switch (p.status()) {
      case "CONFIRMED" -> "CONFIRMED";
      case "FAILED" -> "FAILED";
      case "SIMULATED" -> "SIMULATED";
      case "AWAITING_APPROVAL" -> "AWAITING_APPROVAL";
      case "REJECTED" -> "DENY".equals(p.policyVerdict()) ? "BLOCKED" : "REJECTED";
      default -> "IN_PROGRESS";
    };
  }

  private static String headline(String outcome) {
    return switch (outcome) {
      case "CONFIRMED" -> "Payment confirmed";
      case "FAILED" -> "Payment failed";
      case "BLOCKED" -> "Payment blocked by policy";
      case "REJECTED" -> "Payment rejected by a reviewer";
      case "AWAITING_APPROVAL" -> "Waiting for approval";
      case "SIMULATED" -> "Payment simulated";
      default -> "Awaiting ledger confirmation";
    };
  }

  private static String detail(String outcome, PaymentResponse p) {
    return switch (outcome) {
      case "CONFIRMED" -> "Hedera accepted the transfer.";
      case "FAILED" ->
          PaymentService.NEVER_REACHED.equals(p.failureReason())
              ? "The transfer never reached consensus: no transaction exists on the ledger."
              : "Hedera refused the transfer (" + p.failureReason()
                  + "). No funds moved; the network fee was charged.";
      case "BLOCKED" -> "The policy refused it before execution: no Hedera transaction was created.";
      case "REJECTED" -> "A reviewer refused it: no Hedera transaction was created.";
      case "AWAITING_APPROVAL" -> "The policy holds it until a reviewer approves or rejects it.";
      case "SIMULATED" -> "No Hedera credentials: nothing was transferred.";
      default ->
          p.transactionId() == null
              ? "Not sent to Hedera yet."
              : "Sent to Hedera, final result not known yet. Checking the ledger settles it.";
    };
  }

  private static List<Step> timeline(PaymentResponse p, List<AuditEventEntity> events) {
    List<Step> steps = new ArrayList<>();
    String asset = "HBAR".equals(p.currency()) ? "ℏ" : p.currency();
    steps.add(
        new Step(
            "Payment requested",
            "DONE",
            p.createdAt(),
            p.amount() + " " + asset + " to " + p.destination()
                + (p.requestedById() == null ? "" : ", by " + p.requestedByType() + " " + p.requestedById()),
            null));

    boolean transferEvent = false;
    for (AuditEventEntity e : events) {
      transferEvent |= e.action.startsWith("TRANSFER");
      steps.add(step(p, e));
    }

    if (p.transactionId() == null && "REJECTED".equals(p.status())) {
      steps.add(new Step("No Hedera transaction was created", "NOT_CREATED", null,
          "Stopped before reaching Hedera: nothing was sent and no fee was paid.", null));
    } else if ("AWAITING_APPROVAL".equals(p.status())) {
      steps.add(new Step("Waiting for a reviewer", "WAITING", null,
          "Nothing is sent until someone approves it.", null));
    } else if ("SUBMITTED".equals(p.status()) && !transferEvent) {
      steps.add(new Step("Waiting for the Hedera receipt", "WAITING", null,
          "Transaction " + p.transactionId() + " was sent.", null));
    }
    return steps;
  }

  private static Step step(PaymentResponse p, AuditEventEntity e) {
    JsonNode metadata = metadata(e);
    String reason = metadata.path("failureReason").asText(null);
    String tx = metadata.path("transactionId").asText(null);
    boolean simulated = "SIMULATED".equals(p.status());
    return switch (e.action + ":" + e.status) {
      case "PAYMENT_POLICY:ALLOW", "PAYMENT_POLICY:HOLD", "PAYMENT_POLICY:DENY" ->
          new Step("Policy evaluated: " + e.status, "DENY".equals(e.status) ? "FAILED" : "DONE",
              e.createdAt, policyDetail(p), e.id);
      case "PAYMENT_APPROVAL:APPROVED" ->
          new Step("Approved by a reviewer", "DONE", e.createdAt, null, e.id);
      case "PAYMENT_APPROVAL:REJECTED" ->
          new Step("Rejected by a reviewer", "FAILED", e.createdAt, null, e.id);
      case "TRANSFER:SUCCESS" ->
          simulated
              ? new Step("Transfer simulated", "DONE", e.createdAt,
                  "No Hedera credentials: nothing was sent.", e.id)
              : new Step("Transfer confirmed by Hedera", "DONE", e.createdAt, tx, e.id);
      case "TRANSFER:FAILED" ->
          new Step("Transfer refused by Hedera", "FAILED", e.createdAt,
              reason + (tx == null ? "" : " (" + tx + ")"), e.id);
      case "TRANSFER:UNKNOWN" ->
          new Step("Transfer sent, no receipt from Hedera", "WAITING", e.createdAt,
              "The outcome is settled from the ledger later. " + (tx == null ? "" : tx), e.id);
      case "TRANSFER_SETTLED:CONFIRMED" ->
          new Step("Settled from the ledger: confirmed", "DONE", e.createdAt, tx, e.id);
      case "TRANSFER_SETTLED:FAILED" ->
          new Step("Settled from the ledger: failed", "FAILED", e.createdAt, reason, e.id);
      default -> new Step(e.action + ": " + e.status, "DONE", e.createdAt, null, e.id);
    };
  }

  private static String policyDetail(PaymentResponse p) {
    if ("policy.none".equals(p.policyRuleId())) {
      return "No policy engine connected yet: the payment was not checked.";
    }
    return (p.policyRuleId() == null ? "" : "rule " + p.policyRuleId() + ": ")
        + (p.policyReason() == null ? "" : p.policyReason());
  }

  private static JsonNode metadata(AuditEventEntity e) {
    try {
      return JSON.readTree(e.payload).path("metadata");
    } catch (Exception ex) {
      return JSON.createObjectNode();
    }
  }
}
