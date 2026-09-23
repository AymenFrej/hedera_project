package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.entity.PaymentStatus;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Takes a payment from request to ledger: policy check, optional human approval, transfer, audit.
 *
 * <p>Deliberately not {@code @Transactional}: the transfer is a network call that can take seconds,
 * and each state change must be committed before it so a crash mid-transfer leaves the payment
 * visibly SUBMITTED instead of silently rolled back.
 */
@Service
public class PaymentService {

  public static final String AGENT = "PaymentAgent";
  private static final int HBAR_DECIMALS = 8;

  private final PaymentRepository repository;
  private final HederaPaymentGateway gateway;
  private final PaymentPolicy policy;
  private final AuditService auditService;
  private final HederaProperties properties;
  private final ActorResolver actorResolver;

  public PaymentService(
      PaymentRepository repository,
      HederaPaymentGateway gateway,
      PaymentPolicy policy,
      AuditService auditService,
      HederaProperties properties,
      ActorResolver actorResolver) {
    this.actorResolver = actorResolver;
    this.repository = repository;
    this.gateway = gateway;
    this.policy = policy;
    this.auditService = auditService;
    this.properties = properties;
  }

  /** Records the request, asks the policy, and sends the transfer when it is allowed. */
  public PaymentResponse create(CreatePaymentRequest request) {
    PaymentEntity payment = new PaymentEntity();
    payment.id = "pay_" + UUID.randomUUID();
    payment.destination = request.destination();
    payment.tokenId = blankToNull(request.tokenId());
    payment.currency = payment.tokenId == null ? "HBAR" : payment.tokenId;
    payment.amount = new BigDecimal(request.amount());
    payment.amountUnits = toUnits(payment.amount, payment.tokenId == null);
    payment.envelope =
        blankToNull(request.envelope()) == null
            ? null
            : request.envelope().trim().toUpperCase(Locale.ROOT);
    payment.memo = blankToNull(request.memo());
    Actor requester = actorResolver.currentActor();
    payment.requestedByType = requester.type() == null ? null : requester.type().name();
    payment.requestedById = requester.id();
    payment.status = PaymentStatus.PENDING.name();
    payment.createdAt = Instant.now();
    payment.updatedAt = payment.createdAt;
    payment = repository.save(payment);

    PaymentPolicyDecision decision = policy.evaluate(payment);
    payment.policyVerdict = decision.verdict().name();
    payment.policyRuleId = decision.ruleId();
    payment.policyReason = decision.reason();
    payment.status =
        switch (decision.verdict()) {
          case ALLOW -> PaymentStatus.PENDING.name();
          case HOLD -> PaymentStatus.AWAITING_APPROVAL.name();
          case DENY -> PaymentStatus.REJECTED.name();
        };
    payment = touch(payment);
    audit("PAYMENT_POLICY", decision.verdict().name(), payment);

    if (decision.verdict() == PaymentPolicy.Verdict.ALLOW) {
      payment = execute(payment);
    }
    return toResponse(payment);
  }

  /** A human approves a held payment; it is sent immediately. */
  public PaymentResponse approve(String id) {
    PaymentEntity payment = requireAwaitingApproval(id);
    // Claim the payment first: if two approvals race, the optimistic lock rejects the second one
    // here, before anything is written to the audit trail or sent to Hedera.
    payment.status = PaymentStatus.SUBMITTED.name();
    payment = touch(payment);
    audit("PAYMENT_APPROVAL", "APPROVED", payment);
    return toResponse(execute(payment));
  }

  /** A human rejects a held payment; nothing is sent. */
  public PaymentResponse reject(String id) {
    PaymentEntity payment = requireAwaitingApproval(id);
    payment.status = PaymentStatus.REJECTED.name();
    payment.failureReason = "rejected by a human reviewer";
    payment = touch(payment);
    audit("PAYMENT_APPROVAL", "REJECTED", payment);
    return toResponse(payment);
  }

  public List<PaymentResponse> findAll() {
    return repository.findAll().stream()
        .sorted(
            Comparator.comparing(
                (PaymentEntity p) -> p.createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .map(this::toResponse)
        .toList();
  }

  public PaymentResponse findById(String id) {
    return toResponse(require(id));
  }

  /** True when transfers really reach Hedera. */
  public boolean isLedgerActive() {
    return gateway.isLive();
  }

  private PaymentEntity execute(PaymentEntity payment) {
    // Committed before the network call. With the optimistic lock this is also what stops two
    // concurrent approvals from sending the same payment twice: the second save fails.
    payment.status = PaymentStatus.SUBMITTED.name();
    payment = touch(payment);

    PaymentResult result =
        payment.tokenId == null
            ? gateway.transferHbar(payment.destination, payment.amountUnits, payment.memo)
            : gateway.transferToken(
                payment.tokenId, payment.destination, payment.amountUnits, payment.memo);

    payment.transactionId = result.transactionId();
    payment.sourceAccount = result.sourceAccount();
    if (!result.success()) {
      payment.status = PaymentStatus.FAILED.name();
      payment.failureReason = result.status();
    } else if (result.mock()) {
      payment.status = PaymentStatus.SIMULATED.name();
    } else {
      payment.status = PaymentStatus.CONFIRMED.name();
    }
    payment = touch(payment);
    audit("TRANSFER", result.success() ? "SUCCESS" : "FAILED", payment);
    return payment;
  }

  private PaymentEntity requireAwaitingApproval(String id) {
    PaymentEntity payment = require(id);
    if (!PaymentStatus.AWAITING_APPROVAL.name().equals(payment.status)) {
      throw new IllegalStateException(
          "Payment " + id + " is " + payment.status + ", not AWAITING_APPROVAL");
    }
    return payment;
  }

  private PaymentEntity require(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown payment: " + id));
  }

  private PaymentEntity touch(PaymentEntity payment) {
    payment.updatedAt = Instant.now();
    return repository.saveAndFlush(payment);
  }

  private void audit(String action, String status, PaymentEntity payment) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("paymentId", payment.id);
    metadata.put("amount", payment.amount.toPlainString());
    metadata.put("currency", payment.currency);
    metadata.put("destination", payment.destination);
    putIfPresent(metadata, "envelope", payment.envelope);
    putIfPresent(metadata, "policyRuleId", payment.policyRuleId);
    putIfPresent(metadata, "transactionId", payment.transactionId);
    putIfPresent(metadata, "failureReason", payment.failureReason);
    auditService.record(AGENT, action, status, metadata);
  }

  /**
   * Converts the requested amount to the unit actually sent. HBAR goes to tinybars (8 decimals);
   * token amounts are already in the smallest unit and must be whole.
   */
  static long toUnits(BigDecimal amount, boolean hbar) {
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    try {
      return hbar
          ? amount.movePointRight(HBAR_DECIMALS).longValueExact()
          : amount.longValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          hbar
              ? "HBAR amounts support at most 8 decimals"
              : "token amounts are in the token's smallest unit and must be whole numbers");
    }
  }

  private PaymentResponse toResponse(PaymentEntity p) {
    return new PaymentResponse(
        p.id,
        p.amount == null ? null : p.amount.toPlainString(),
        p.currency,
        p.tokenId,
        p.destination,
        p.envelope,
        p.memo,
        p.status,
        p.sourceAccount,
        p.transactionId,
        p.transactionId == null
            ? null
            : "https://hashscan.io/%s/transaction/%s"
                .formatted(properties.getNetwork(), p.transactionId),
        p.policyVerdict,
        p.policyRuleId,
        p.policyReason,
        p.failureReason,
        p.requestedByType,
        p.requestedById,
        p.createdAt,
        p.updatedAt);
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
