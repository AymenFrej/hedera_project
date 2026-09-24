package com.hedera.agentplatform.policies;

import java.util.EnumMap;
import java.util.Map;

/**
 * The agent's decision layer. Pure: no network, no SDK, no model.
 *
 * <p>A decision is a value computed from (request, budget, rules). That matters twice over: it can
 * be replayed offline by anyone, and it is the decision — not the execution — that gets anchored to
 * the HCS audit trail. The model may phrase a request; these rules settle it.
 */
public final class PolicyEngine {

  /** Envelopes of a relocation runway. */
  public enum Envelope {
    RENT,
    ESSENTIALS,
    EMERGENCY
  }

  private static final double RENT_SHARE = 0.5;
  private static final double ESSENTIALS_SHARE = 0.3;

  /**
   * Spending more than this fraction of an envelope's remaining balance in one move needs a human.
   */
  private static final double SINGLE_MOVE_HOLD_RATIO = 0.5;

  private PolicyEngine() {}

  /**
   * Split a total runway into envelopes. The remainder lands in EMERGENCY so the envelopes always
   * sum to exactly the total: no funds created, none lost.
   *
   * @param total units of the HTS token, smallest unit
   */
  public static Map<Envelope, Long> allocate(long total) {
    if (total <= 0) {
      throw new IllegalArgumentException("total must be a positive integer, got " + total);
    }
    long rent = (long) Math.floor(total * RENT_SHARE);
    long essentials = (long) Math.floor(total * ESSENTIALS_SHARE);
    Map<Envelope, Long> envelopes = new EnumMap<>(Envelope.class);
    envelopes.put(Envelope.RENT, rent);
    envelopes.put(Envelope.ESSENTIALS, essentials);
    envelopes.put(Envelope.EMERGENCY, total - rent - essentials);
    return Map.copyOf(envelopes);
  }

  /**
   * Settle a spend request. Pure: the state passed in is never mutated.
   *
   * <p>Ordering is the rule, not an implementation detail. Every DENY is checked before every HOLD,
   * so a decision that cannot settle is never put in front of a human to approve.
   */
  public static PolicyDecision decide(PolicyRequest request, PolicyState state) {
    Envelope envelope = request.envelope();
    long amount = request.amount();

    if (envelope == null) {
      String named = request.envelopeName();
      String reason =
          named == null || named.isBlank()
              ? "no envelope named in the request"
              : "there is no envelope called \"" + named + "\"";
      return new PolicyDecision(Verdict.DENY, "envelope.unknown", reason, request, null);
    }
    if (amount <= 0) {
      return new PolicyDecision(
          Verdict.DENY,
          "amount.invalid",
          "amount must be a positive integer, got " + amount,
          request,
          null);
    }
    if (request.counterparty() == null || request.counterparty().isBlank()) {
      return new PolicyDecision(
          Verdict.DENY,
          "counterparty.missing",
          "no counterparty named in the request",
          request,
          null);
    }
    Long balance = state.balances().get(envelope);
    if (balance == null) {
      return new PolicyDecision(
          Verdict.DENY,
          "envelope.unfunded",
          "envelope \"" + name(envelope) + "\" is not funded",
          request,
          null);
    }
    if (amount > balance) {
      return new PolicyDecision(
          Verdict.DENY,
          "funds.insufficient",
          "requested " + amount + " but " + name(envelope) + " holds " + balance,
          request,
          null);
    }

    long balanceAfter = balance - amount;

    if (envelope == Envelope.EMERGENCY) {
      return new PolicyDecision(
          Verdict.HOLD,
          "emergency.human",
          "emergency funds always require human approval",
          request,
          balanceAfter);
    }
    if (!state.knownCounterparties().contains(request.counterparty())) {
      return new PolicyDecision(
          Verdict.HOLD,
          "counterparty.unknown",
          "first transfer to " + request.counterparty(),
          request,
          balanceAfter);
    }
    if (amount > balance * SINGLE_MOVE_HOLD_RATIO) {
      return new PolicyDecision(
          Verdict.HOLD,
          "amount.large",
          amount + " exceeds 50% of the remaining " + name(envelope) + " envelope",
          request,
          balanceAfter);
    }

    return new PolicyDecision(
        Verdict.ALLOW,
        "policy.ok",
        "within " + name(envelope) + " envelope and under limits",
        request,
        balanceAfter);
  }

  private static String name(Envelope envelope) {
    return envelope.name().toLowerCase(java.util.Locale.ROOT);
  }
}
