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
import com.hedera.agentplatform.payments.dto.PaymentReceipt.SafetyRow;
import com.hedera.agentplatform.payments.dto.PaymentReceipt.Step;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.dto.PolicyExplanation;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
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
  private final PaymentMirrorClient mirror;

  public PaymentReceiptService(
      PaymentService payments,
      PaymentAuditLookup auditLookup,
      AuditService auditService,
      HederaProperties properties,
      PaymentMirrorClient mirror) {
    this.mirror = mirror;
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

    List<AuditEventEntity> events = eventsOf(payment);
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
        safety(payment, ledger, events, audit),
        properties.getNetwork());
  }

  private List<AuditEventEntity> eventsOf(PaymentResponse p) {
    return auditLookup.eventsFor(p.id(), p.policyAuditEventId(), p.approvalId());
  }

  /** The audit events of one payment, as the audit module reports them. */
  public List<AuditEventResponse> auditEvents(String paymentId) {
    return eventsOf(payments.findById(paymentId)).stream()
        .map(e -> auditService.findById(e.id))
        .toList();
  }

  /** Reads one audit event of this payment back from HCS, through the audit module. */
  public VerificationResult verifyAuditEvent(String paymentId, String eventId) {
    boolean belongs =
        eventsOf(payments.findById(paymentId)).stream().anyMatch(e -> e.id.equals(eventId));
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

  /**
   * The safety summary: one row per check, each from a fact. A row that cannot be backed by a
   * fact says so (UNKNOWN or NOT_APPLICABLE) instead of showing a tick.
   */
  private List<SafetyRow> safety(
      PaymentResponse p,
      PaymentVerification ledger,
      List<AuditEventEntity> events,
      List<AuditProof> audit) {
    List<SafetyRow> rows = new ArrayList<>();

    rows.add(
        "USER".equals(p.requestedByType())
            ? new SafetyRow("Identity", "PASS", "Signed-in user " + p.requestedById())
            : new SafetyRow("Identity", "NOT_APPLICABLE",
                "Not verified: no login yet, attributed to " + p.requestedById()));

    rows.add(recipientRow(p, ledger));

    PolicyExplanation why = p.policyExplanation();
    if (why == null || "policy.none".equals(p.policyRuleId())) {
      rows.add(new SafetyRow("Policy", "UNKNOWN", "No policy engine decided on this payment"));
    } else {
      rows.add(new SafetyRow("Policy",
          switch (why.verdict()) {
            case "ALLOW" -> "PASS";
            case "HOLD" -> "WAITING";
            default -> "FAIL";
          },
          why.verdict() + " · " + why.ruleId() + " · " + why.reason()));
      if (why.envelope() != null) {
        rows.add(why.shortfall() != null
            ? new SafetyRow("Envelope", "FAIL", why.envelope().toLowerCase()
                + " held " + why.available() + ", short by " + why.shortfall())
            : new SafetyRow("Envelope", why.available() == null ? "FAIL" : "PASS",
                why.available() == null
                    ? why.envelope().toLowerCase() + " is not funded"
                    : why.envelope().toLowerCase() + " held " + why.available()));
      }
    }

    if (p.keepAtLeast() != null) {
      boolean failed = events.stream().anyMatch(e -> e.action.equals("PAYMENT_CONDITION") && e.status.equals("FAILED"));
      boolean passed = events.stream().anyMatch(e -> e.action.equals("PAYMENT_CONDITION") && e.status.equals("PASSED"));
      rows.add(failed
          ? new SafetyRow("Your condition", "FAIL", p.failureReason())
          : passed
              ? new SafetyRow("Your condition", "PASS", "keep at least " + p.keepAtLeast() + ": met")
              : new SafetyRow("Your condition", "UNKNOWN", "Not checked: simulation mode"));
    }

    rows.add(approvalRow(p, events));
    rows.add(executionRow(p));

    if (ledger == null) {
      rows.add(new SafetyRow("Ledger", "NOT_APPLICABLE",
          p.transactionId() == null ? "No transaction to check" : "Not checked: simulation mode"));
    } else if (ledger.ledgerResult() == null) {
      rows.add(new SafetyRow("Ledger", PaymentService.NEVER_REACHED.equals(p.failureReason())
          ? "NOT_APPLICABLE" : "UNKNOWN", ledger.detail()));
    } else {
      rows.add(new SafetyRow("Ledger", ledger.verified() ? "PASS" : "FAIL", ledger.detail()));
    }

    long anchored = audit.stream().filter(a -> a.verified() != null).count();
    long verified = audit.stream().filter(a -> Boolean.TRUE.equals(a.verified())).count();
    if (audit.isEmpty()) {
      rows.add(new SafetyRow("Audit", "UNKNOWN", "No audit event found for this payment"));
    } else if (anchored == 0) {
      rows.add(new SafetyRow("Audit", "UNKNOWN",
          audit.size() + " event(s) recorded locally only, not on HCS"));
    } else if (verified == audit.size()) {
      rows.add(new SafetyRow("Audit", "PASS",
          "All " + verified + " event(s) read back from HCS and matching"));
    } else {
      rows.add(new SafetyRow("Audit", "FAIL",
          verified + " of " + audit.size() + " event(s) verified on HCS"));
    }
    return rows;
  }

  private SafetyRow recipientRow(PaymentResponse p, PaymentVerification ledger) {
    if (ledger != null) {
      boolean received = ledger.checks().stream()
          .anyMatch(c -> c.name().equals("Recipient received") && c.ok());
      if (received) {
        return new SafetyRow("Recipient", "PASS", p.destination() + " received it (Mirror Node)");
      }
    }
    if (!payments.isLedgerActive()) {
      return new SafetyRow("Recipient", "UNKNOWN", "Not checked: simulation mode");
    }
    var account = mirror.findAccount(p.destination());
    return switch (account.state()) {
      case FOUND -> account.account().deleted()
          ? new SafetyRow("Recipient", "FAIL", p.destination() + " has been deleted")
          : new SafetyRow("Recipient", "PASS", p.destination() + " is a valid "
              + properties.getNetwork() + " account");
      case NOT_FOUND -> new SafetyRow("Recipient", "FAIL",
          "No account " + p.destination() + " on " + properties.getNetwork());
      case UNAVAILABLE -> new SafetyRow("Recipient", "UNKNOWN", "Mirror Node could not be reached");
    };
  }

  private static SafetyRow approvalRow(PaymentResponse p, List<AuditEventEntity> events) {
    for (AuditEventEntity e : events) {
      if (e.action.endsWith("_APPROVAL")) {
        switch (e.status) {
          case "APPROVED" -> {
            return new SafetyRow("Approval", "PASS", "Approved by a reviewer");
          }
          case "REJECTED" -> {
            return new SafetyRow("Approval", "FAIL", "Rejected by a reviewer");
          }
          case "REFUSED" -> {
            return new SafetyRow("Approval", "FAIL", "Could not be approved: " + p.failureReason());
          }
          default -> { }
        }
      }
    }
    if ("AWAITING_APPROVAL".equals(p.status())) {
      return new SafetyRow("Approval", "WAITING", "Waiting for a reviewer");
    }
    if ("DENY".equals(p.policyVerdict())) {
      return new SafetyRow("Approval", "NOT_APPLICABLE", "Not reached: the policy refused it");
    }
    return new SafetyRow("Approval", "NOT_APPLICABLE", "Not required by the policy");
  }

  private static SafetyRow executionRow(PaymentResponse p) {
    return switch (p.status()) {
      case "CONFIRMED" -> new SafetyRow("Execution", "PASS", "Confirmed by Hedera");
      case "FAILED" -> PaymentService.NEVER_REACHED.equals(p.failureReason())
          ? new SafetyRow("Execution", "FAIL", "Sent, never reached consensus")
          : new SafetyRow("Execution", "FAIL", "Refused by Hedera: " + p.failureReason());
      case "SUBMITTED" -> new SafetyRow("Execution", "WAITING", "Sent, no final result yet");
      case "SIMULATED" -> new SafetyRow("Execution", "NOT_APPLICABLE",
          "Simulated: nothing was sent");
      default -> p.transactionId() == null
          ? new SafetyRow("Execution", "NOT_APPLICABLE", "No Hedera transaction was created")
          : new SafetyRow("Execution", "WAITING", "Not final yet");
    };
  }

  private static String outcome(PaymentResponse p) {
    return switch (p.status()) {
      case "CONFIRMED" -> "CONFIRMED";
      case "FAILED" -> "FAILED";
      case "SIMULATED" -> "SIMULATED";
      case "AWAITING_APPROVAL" -> "AWAITING_APPROVAL";
      case "REJECTED" ->
          "DENY".equals(p.policyVerdict())
              ? "BLOCKED"
              : p.policyVerdict() == null && p.keepAtLeast() != null
                  ? "CONDITION_NOT_MET"
                  : "REJECTED";
      default -> "IN_PROGRESS";
    };
  }

  private static String headline(String outcome) {
    return switch (outcome) {
      case "CONFIRMED" -> "Payment confirmed";
      case "FAILED" -> "Payment failed";
      case "BLOCKED" -> "Payment blocked by policy";
      case "CONDITION_NOT_MET" -> "Payment stopped by your condition";
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
      case "CONDITION_NOT_MET" -> p.failureReason() + ". No Hedera transaction was created.";
      case "REJECTED" ->
          "rejected by a human reviewer".equals(p.failureReason())
              ? "A reviewer refused it: no Hedera transaction was created."
              : "It could not be approved (" + p.failureReason()
                  + "): no Hedera transaction was created.";
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
      case "POLICY_DECISION:ALLOW", "POLICY_DECISION:HOLD", "POLICY_DECISION:DENY" ->
          new Step("Policy evaluated: " + e.status, "DENY".equals(e.status) ? "FAILED" : "DONE",
              e.createdAt, policyDetail(p) + " (recorded by the Policies module)", e.id);
      case "PAYMENT_CONDITION:PASSED" ->
          new Step("Your condition checked", "DONE", e.createdAt,
              "keep at least " + p.keepAtLeast() + ": met on the real balance", e.id);
      case "PAYMENT_CONDITION:FAILED" ->
          new Step("Your condition not met", "FAILED", e.createdAt, p.failureReason(), e.id);
      case "POLICY_APPROVAL:APPROVED" ->
          new Step("Approved by a reviewer", "DONE", e.createdAt,
              "in the approvals queue, which checked it was still affordable", e.id);
      case "POLICY_APPROVAL:REJECTED" ->
          new Step("Rejected by a reviewer", "FAILED", e.createdAt, "in the approvals queue", e.id);
      case "PAYMENT_APPROVAL:REFUSED" ->
          new Step("Approval refused", "FAILED", e.createdAt, reason, e.id);
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
