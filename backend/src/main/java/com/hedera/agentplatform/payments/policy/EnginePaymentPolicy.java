package com.hedera.agentplatform.payments.policy;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import com.hedera.agentplatform.policies.PolicyDecision;
import com.hedera.agentplatform.policies.PolicyEngine;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.PolicyRequest;
import com.hedera.agentplatform.policies.PolicyState;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asks the Policies module's {@link PolicyEngine} about a payment.
 *
 * <p>The decision is entirely the engine's. What Payments supplies is the state the engine decides
 * on, because Payments owns the payment history it comes from:
 *
 * <ul>
 *   <li><b>Envelope balances.</b> A runway is configured per asset ({@code payments.runways}, e.g.
 *       {@code 0.0.10687138=1000,HBAR=5000000000}, in smallest units). The engine's own {@link
 *       PolicyEngine#allocate} splits it into envelopes once; each envelope then shrinks by the
 *       payments already committed from it. An asset without a runway has no funded envelope.
 *   <li><b>Known counterparties.</b> Accounts this platform has already paid.
 * </ul>
 *
 * <p>Pure: it reads and decides, it records nothing. The payment preview relies on that, and
 * Payments writes the decision to the audit trail itself, with the payment id, once per payment.
 */
@Component
public class EnginePaymentPolicy implements PaymentPolicy {

  private static final Logger log = LoggerFactory.getLogger(EnginePaymentPolicy.class);

  /** Payments whose money is gone or on its way: they use up their envelope. */
  static final List<String> COMMITTED = List.of("SUBMITTED", "CONFIRMED", "SIMULATED");

  /** Payments that actually reached the recipient. */
  static final List<String> PAID = List.of("CONFIRMED", "SIMULATED");

  private final PaymentRepository payments;
  private final Map<String, Long> runways;

  public EnginePaymentPolicy(
      PaymentRepository payments, @Value("${payments.runways:}") String runways) {
    this.payments = payments;
    this.runways = parseRunways(runways);
    if (this.runways.isEmpty()) {
      log.warn(
          "No payment runway configured (payments.runways): every envelope is unfunded and the"
              + " policy engine will deny payments. Example: PAYMENT_RUNWAYS=HBAR=5000000000");
    } else {
      log.info("Payment runways (smallest units): {}", this.runways);
    }
  }

  @Override
  public PaymentPolicyDecision evaluate(PaymentEntity payment) {
    PolicyDecision d =
        PolicyEngine.decide(
            new PolicyRequest(envelope(payment.envelope), payment.amountUnits, payment.destination),
            state(payment.currency));
    return new PaymentPolicyDecision(Verdict.valueOf(d.verdict().name()), d.ruleId(), d.reason());
  }

  /** What is left in each envelope of this asset's runway, and who has been paid before. */
  public PolicyState state(String asset) {
    Map<Envelope, Long> balances = new EnumMap<>(Envelope.class);
    Long runway = runways.get(asset);
    if (runway != null) {
      balances.putAll(PolicyEngine.allocate(runway));
      for (Object[] row : payments.committedByEnvelope(asset, COMMITTED)) {
        Envelope e = envelope((String) row[0]);
        if (e != null) {
          balances.computeIfPresent(e, (k, left) -> left - ((Number) row[1]).longValue());
        }
      }
    }
    return new PolicyState(balances, payments.paidDestinations(PAID));
  }

  /** The configured runways, for display. */
  public Map<String, Long> runways() {
    return runways;
  }

  private static Envelope envelope(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return Envelope.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static Map<String, Long> parseRunways(String config) {
    Map<String, Long> parsed = new LinkedHashMap<>();
    if (config == null || config.isBlank()) {
      return parsed;
    }
    for (String entry : config.split(",")) {
      String[] parts = entry.trim().split("=");
      if (parts.length != 2) {
        throw new IllegalArgumentException(
            "payments.runways entries look like ASSET=UNITS, got '" + entry.trim() + "'");
      }
      String asset = parts[0].trim().toUpperCase(Locale.ROOT).equals("HBAR") ? "HBAR" : parts[0].trim();
      long units = Long.parseLong(parts[1].trim());
      if (units <= 0) {
        throw new IllegalArgumentException("payments.runways total must be positive for " + asset);
      }
      parsed.put(asset, units);
    }
    return parsed;
  }
}
