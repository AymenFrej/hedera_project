package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.payments.dto.BalanceResponse;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.entity.PaymentStatus;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway.PaymentResult;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorTransaction;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
  static final Duration SETTLE_AFTER = Duration.ofMinutes(5);
  private static final String IDEMPOTENCY_KEY = "[A-Za-z0-9_:-]{8,64}";
  /** A transfer the ledger never saw: there is nothing to link to on HashScan. */
  static final String NEVER_REACHED = "never reached consensus";

  private final PaymentRepository repository;
  private final HederaPaymentGateway gateway;
  private final PaymentPolicy policy;
  private final AuditService auditService;
  private final HederaProperties properties;
  private final ActorResolver actorResolver;
  private final PaymentMirrorClient mirrorClient;
  private final PolicyLocks policyLocks;

  public PaymentService(
      PaymentRepository repository,
      HederaPaymentGateway gateway,
      PaymentPolicy policy,
      AuditService auditService,
      HederaProperties properties,
      ActorResolver actorResolver,
      PaymentMirrorClient mirrorClient,
      PolicyLocks policyLocks) {
    this.policyLocks = policyLocks;
    this.actorResolver = actorResolver;
    this.mirrorClient = mirrorClient;
    this.repository = repository;
    this.gateway = gateway;
    this.policy = policy;
    this.auditService = auditService;
    this.properties = properties;
  }

  /** Records the request, asks the policy, and sends the transfer when it is allowed. */
  public PaymentResponse create(CreatePaymentRequest request) {
    return create(request, null);
  }

  /**
   * Same, deduplicated by {@code idempotencyKey}: a request repeated with the same key (double
   * click, retry after a network error) returns the payment already created instead of paying
   * twice. Reusing a key for a different payment is refused.
   */
  public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {
    String key = blankToNull(idempotencyKey);
    if (key != null) {
      if (!key.matches(IDEMPOTENCY_KEY)) {
        throw new IllegalArgumentException(
            "Idempotency-Key must be 8 to 64 letters, digits, '-', '_' or ':'");
      }
      var existing = repository.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        return replay(existing.get(), request);
      }
    }

    PaymentEntity payment = draft(request);
    payment.idempotencyKey = key;
    try {
      payment = repository.saveAndFlush(payment);
    } catch (DataIntegrityViolationException e) {
      // Two identical requests raced past the lookup above; the unique key let only one in.
      if (key == null) {
        throw e;
      }
      return replay(repository.findByIdempotencyKey(key).orElseThrow(() -> e), request);
    }

    // Decide and commit the verdict under the asset's lock: an allowed payment counts against its
    // envelope from this point on, so a concurrent payment is decided on the reduced balance.
    PaymentEntity saved = payment;
    PaymentPolicyDecision decision =
        policyLocks.withLock(saved.currency, () -> decideAndCommit(saved));
    payment = repository.findById(saved.id).orElseThrow();
    audit("PAYMENT_POLICY", decision.verdict().name(), payment);

    if (decision.verdict() == PaymentPolicy.Verdict.ALLOW) {
      payment = execute(payment);
    }
    return toResponse(payment);
  }

  /**
   * The payment a request describes, not saved. The preview evaluates the policy on exactly this,
   * so what it shows is what execution would be asked.
   */
  PaymentEntity draft(CreatePaymentRequest request) {
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
    return payment;
  }

  /** Returns the payment a key already created, provided it is the same payment. */
  private PaymentResponse replay(PaymentEntity existing, CreatePaymentRequest request) {
    boolean same =
        existing.destination.equals(request.destination())
            && existing.amount.compareTo(new BigDecimal(request.amount())) == 0
            && java.util.Objects.equals(existing.tokenId, blankToNull(request.tokenId()));
    if (!same) {
      throw new IllegalStateException(
          "Idempotency-Key already used for a different payment (" + existing.id + ")");
    }
    return toResponse(existing);
  }

  private PaymentPolicyDecision decideAndCommit(PaymentEntity payment) {
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
    touch(payment);
    return decision;
  }

  /**
   * A human approves a held payment; it is sent immediately.
   *
   * <p>The policy is asked again first. A reviewer answers a HOLD, not a DENY: if the envelope was
   * used up while the payment waited, approving it would spend money the envelope no longer has,
   * so the payment is refused instead.
   */
  public PaymentResponse approve(String id) {
    PaymentEntity held = requireAwaitingApproval(id);
    PaymentEntity payment =
        policyLocks.withLock(
            held.currency,
            () -> {
              PaymentPolicyDecision now = policy.evaluate(held);
              if (now.verdict() == PaymentPolicy.Verdict.DENY) {
                held.policyVerdict = now.verdict().name();
                held.policyRuleId = now.ruleId();
                held.policyReason = now.reason();
                held.status = PaymentStatus.REJECTED.name();
                held.failureReason = "the policy now refuses it: " + now.reason();
                return touch(held);
              }
              // Claim the payment inside the lock: it counts against its envelope from here, and
              // if two approvals race, the optimistic lock rejects the second one.
              held.status = PaymentStatus.SUBMITTED.name();
              return touch(held);
            });
    if (PaymentStatus.REJECTED.name().equals(payment.status)) {
      audit("PAYMENT_POLICY", "DENY", payment);
      return toResponse(payment);
    }
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

  /** Balances of the account payments leave from, read from the Mirror Node. */
  public BalanceResponse balance() {
    String account = gateway.payerAccount();
    if (account == null) {
      return new BalanceResponse(
          false,
          "Simulation mode: no Hedera account is configured",
          null,
          null,
          List.of(),
          null,
          null);
    }
    String explorer = "https://hashscan.io/%s/account/%s".formatted(properties.getNetwork(), account);
    BalanceLookup lookup = mirrorClient.findBalances(account);
    return switch (lookup.state()) {
      case UNAVAILABLE ->
          new BalanceResponse(
              false, "Mirror Node could not be reached, try again", account, null, List.of(), null,
              explorer);
      case NOT_FOUND ->
          new BalanceResponse(
              false, "The Mirror Node does not know account " + account, account, null, List.of(),
              null, explorer);
      case FOUND -> {
        AccountBalances b = lookup.balances();
        BalanceResponse.Asset hbar =
            new BalanceResponse.Asset(
                null, "HBAR", "HBAR", HBAR_DECIMALS, b.tinybars(), decimal(b.tinybars(), HBAR_DECIMALS));
        List<BalanceResponse.Asset> tokens =
            b.tokens().stream()
                .map(
                    t ->
                        new BalanceResponse.Asset(
                            t.tokenId(),
                            t.symbol(),
                            t.name(),
                            t.decimals(),
                            t.balance(),
                            decimal(t.balance(), t.decimals())))
                .toList();
        yield new BalanceResponse(
            true, null, account, hbar, tokens, consensusInstant(b.timestamp()), explorer);
      }
    };
  }

  /** True when transfers really reach Hedera. */
  public boolean isLedgerActive() {
    return gateway.isLive();
  }

  private PaymentEntity execute(PaymentEntity payment) {
    // Committed before the network call, together with the transaction id the transfer will use:
    // if the backend dies mid-transfer, that id is how the Mirror Node tells us what happened.
    // With the optimistic lock this is also what stops two concurrent approvals from sending the
    // same payment twice: the second save fails.
    payment.status = PaymentStatus.SUBMITTED.name();
    payment.transactionId = gateway.newTransactionId();
    payment = touch(payment);

    PaymentResult result =
        payment.tokenId == null
            ? gateway.transferHbar(
                payment.transactionId, payment.destination, payment.amountUnits, payment.memo)
            : gateway.transferToken(
                payment.transactionId,
                payment.tokenId,
                payment.destination,
                payment.amountUnits,
                payment.memo);

    payment.sourceAccount = result.sourceAccount();
    switch (result.outcome()) {
      case SUCCESS ->
          payment.status =
              result.mock() ? PaymentStatus.SIMULATED.name() : PaymentStatus.CONFIRMED.name();
      case FAILED -> {
        payment.status = PaymentStatus.FAILED.name();
        payment.failureReason = result.status();
      }
      case UNKNOWN -> {
        // Stays SUBMITTED: the transfer may have happened. Verification settles it.
        payment.failureReason = "no receipt from Hedera yet: " + result.status();
      }
    }
    payment = touch(payment);
    audit("TRANSFER", result.outcome().name(), payment);
    return payment;
  }

  /**
   * Compares the payment with what the Mirror Node holds, field by field. A payment left SUBMITTED
   * long enough ago (e.g. the backend restarted mid-transfer) is settled from the ledger here.
   */
  public PaymentVerification verify(String id) {
    PaymentEntity payment = require(id);

    if (payment.transactionId == null) {
      String detail =
          switch (PaymentStatus.valueOf(payment.status)) {
            case REJECTED -> "Blocked before reaching Hedera: no transaction was created";
            case SIMULATED -> "Simulated: no Hedera credentials, nothing was transferred";
            case AWAITING_APPROVAL -> "Waiting for approval: not sent to Hedera yet";
            default -> "No Hedera transaction for this payment";
          };
      return new PaymentVerification(
          false, detail, payment.status, null, null, null, List.of(), null);
    }

    MirrorLookup lookup = mirrorClient.findTransaction(payment.transactionId);
    if (PaymentStatus.SUBMITTED.name().equals(payment.status)) {
      payment = settleFromLedger(payment, lookup);
    }
    // After settling: a payment just found to have never reached the ledger gets no link.
    String explorerUrl = explorerUrl(payment);

    return switch (lookup.state()) {
      case UNAVAILABLE ->
          new PaymentVerification(
              false,
              "Mirror Node could not be reached, try again",
              payment.status,
              payment.transactionId,
              null,
              null,
              List.of(),
              explorerUrl);
      case NOT_FOUND ->
          new PaymentVerification(
              false,
              PaymentStatus.FAILED.name().equals(payment.status)
                  ? "Not on the ledger: the transfer never reached consensus"
                  : "Mirror Node has no record of this transaction yet",
              payment.status,
              payment.transactionId,
              null,
              null,
              List.of(),
              explorerUrl);
      case FOUND -> compare(payment, lookup.transaction(), explorerUrl);
    };
  }

  /** Settles every payment left SUBMITTED long enough ago. Called at startup. */
  public int settleStalePayments() {
    int settled = 0;
    for (PaymentEntity payment : repository.findByStatus(PaymentStatus.SUBMITTED.name())) {
      if (payment.transactionId == null || !isStale(payment)) {
        continue;
      }
      PaymentEntity after =
          settleFromLedger(payment, mirrorClient.findTransaction(payment.transactionId));
      if (!PaymentStatus.SUBMITTED.name().equals(after.status)) {
        settled++;
      }
    }
    return settled;
  }

  /**
   * Only touches a payment nobody is still working on: a transfer in flight in this process can
   * take up to two SDK timeouts, and settling it underneath would race with its own update.
   */
  private PaymentEntity settleFromLedger(PaymentEntity payment, MirrorLookup lookup) {
    if (!isStale(payment)) {
      return payment;
    }
    switch (lookup.state()) {
      case FOUND -> {
        MirrorTransaction tx = lookup.transaction();
        boolean success = "SUCCESS".equals(tx.result());
        payment.status = success ? PaymentStatus.CONFIRMED.name() : PaymentStatus.FAILED.name();
        payment.failureReason = success ? null : tx.result();
        if (payment.sourceAccount == null) {
          payment.sourceAccount = payerOf(payment);
        }
      }
      case NOT_FOUND -> {
        // Past its validity window and unknown to the ledger: it can no longer execute.
        payment.status = PaymentStatus.FAILED.name();
        payment.failureReason = NEVER_REACHED;
      }
      case UNAVAILABLE -> {
        return payment;
      }
    }
    payment = touch(payment);
    audit("TRANSFER_SETTLED", payment.status, payment);
    return payment;
  }

  /**
   * Older than the SDK's own retry budget (submit + receipt timeouts) plus the transaction's
   * validity window: whatever was going to happen has happened.
   */
  private static boolean isStale(PaymentEntity payment) {
    return payment.updatedAt == null
        || payment.updatedAt.isBefore(Instant.now().minus(SETTLE_AFTER));
  }

  private PaymentVerification compare(
      PaymentEntity payment, MirrorTransaction tx, String explorerUrl) {
    List<PaymentVerification.Check> checks = new ArrayList<>();
    String source = payment.sourceAccount != null ? payment.sourceAccount : payerOf(payment);

    if (PaymentStatus.FAILED.name().equals(payment.status)) {
      // A refused transfer still reaches consensus (and costs its fee); what matters is that the
      // ledger agrees it failed and that nothing reached the recipient.
      long received =
          payment.tokenId == null
              ? tx.hbarTransfers().getOrDefault(payment.destination, 0L)
              : tx.tokenChange(payment.tokenId, payment.destination);
      checks.add(new PaymentVerification.Check("Network result",
          payment.failureReason == null ? "not SUCCESS" : payment.failureReason, tx.result(),
          !"SUCCESS".equals(tx.result())
              && (payment.failureReason == null || payment.failureReason.equals(tx.result()))));
      checks.add(new PaymentVerification.Check("Nothing reached the recipient", "0",
          String.valueOf(received), received == 0));
      boolean consistent = checks.stream().allMatch(PaymentVerification.Check::ok);
      return new PaymentVerification(
          consistent,
          consistent
              ? "Ledger confirms the transfer failed (" + tx.result()
                  + "); the network fee was still charged"
              : "Ledger does not match the failure we recorded: see the failed checks",
          payment.status,
          payment.transactionId,
          tx.result(),
          tx.consensusTimestamp(),
          checks,
          explorerUrl);
    }

    checks.add(new PaymentVerification.Check("Network result", "SUCCESS", tx.result(),
        "SUCCESS".equals(tx.result())));
    if (payment.tokenId == null) {
      long received = tx.hbarTransfers().getOrDefault(payment.destination, 0L);
      long paid = -tx.hbarTransfers().getOrDefault(source, 0L);
      checks.add(new PaymentVerification.Check("Recipient received",
          hbar(payment.amountUnits) + " to " + payment.destination, hbar(received),
          received == payment.amountUnits));
      // The sender's HBAR change includes the network fee, so it is at least the amount.
      checks.add(new PaymentVerification.Check("Sender paid",
          "at least " + hbar(payment.amountUnits) + " from " + source,
          hbar(paid) + " (fee included)", paid >= payment.amountUnits));
    } else {
      long received = tx.tokenChange(payment.tokenId, payment.destination);
      long sent = -tx.tokenChange(payment.tokenId, source);
      checks.add(new PaymentVerification.Check("Recipient received",
          payment.amountUnits + " of " + payment.tokenId + " to " + payment.destination,
          received + " of " + payment.tokenId, received == payment.amountUnits));
      checks.add(new PaymentVerification.Check("Sender sent",
          payment.amountUnits + " of " + payment.tokenId + " from " + source,
          sent + " of " + payment.tokenId, sent == payment.amountUnits));
    }

    boolean verified = checks.stream().allMatch(PaymentVerification.Check::ok);
    return new PaymentVerification(
        verified,
        verified
            ? "Ledger transaction matches the payment"
            : "Ledger transaction does not match the payment: see the failed checks",
        payment.status,
        payment.transactionId,
        tx.result(),
        tx.consensusTimestamp(),
        checks,
        explorerUrl);
  }

  /** The payer is the account part of the transaction id, e.g. 0.0.5239440 in 0.0.5239440@… */
  private static String payerOf(PaymentEntity payment) {
    int at = payment.transactionId.indexOf('@');
    return at < 0 ? null : payment.transactionId.substring(0, at);
  }

  private static String decimal(long units, int decimals) {
    return BigDecimal.valueOf(units, decimals).toPlainString();
  }

  /** Mirror Node timestamps are {@code seconds.nanos}. */
  static Instant consensusInstant(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return null;
    }
    String[] parts = timestamp.split("\\.");
    long nanos = parts.length > 1 ? Long.parseLong((parts[1] + "000000000").substring(0, 9)) : 0;
    return Instant.ofEpochSecond(Long.parseLong(parts[0]), nanos);
  }

  private static String hbar(long tinybars) {
    return BigDecimal.valueOf(tinybars, HBAR_DECIMALS).stripTrailingZeros().toPlainString() + " ℏ";
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
    metadata.put("amount", payment.amount.stripTrailingZeros().toPlainString());
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
        p.amount == null ? null : p.amount.stripTrailingZeros().toPlainString(),
        p.currency,
        p.tokenId,
        p.destination,
        p.envelope,
        p.memo,
        p.status,
        p.sourceAccount,
        p.transactionId,
        explorerUrl(p),
        p.policyVerdict,
        p.policyRuleId,
        p.policyReason,
        p.failureReason,
        p.requestedByType,
        p.requestedById,
        p.createdAt,
        p.updatedAt);
  }

  /** Only once the transfer was actually sent: a mock payment has nothing to show on HashScan. */
  private String explorerUrl(PaymentEntity p) {
    if (p.transactionId == null || !gateway.isLive() || NEVER_REACHED.equals(p.failureReason)) {
      return null;
    }
    return "https://hashscan.io/%s/transaction/%s".formatted(properties.getNetwork(), p.transactionId);
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
