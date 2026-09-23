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
}
