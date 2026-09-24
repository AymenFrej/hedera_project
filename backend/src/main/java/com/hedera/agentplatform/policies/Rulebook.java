package com.hedera.agentplatform.policies;

import java.util.List;

/**
 * The rules the engine applies, published so they can be read before a spend rather than inferred
 * from a verdict after one. Every id here is emitted by {@link PolicyEngine#decide}; the rulebook
 * test drives real requests through the engine and fails if one is missing.
 */
public final class Rulebook {

  /** One rule: its stable id, the verdict it produces, and why in a sentence. */
  public record Rule(String ruleId, Verdict verdict, String reason) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule(
              "envelope.unknown",
              Verdict.DENY,
              "the request names no envelope, or names one that does not exist"),
          new Rule("amount.invalid", Verdict.DENY, "the amount must be a positive whole number"),
          new Rule(
              "counterparty.missing",
              Verdict.DENY,
              "the request names nobody to pay, so it can never settle"),
          new Rule("envelope.unfunded", Verdict.DENY, "that envelope holds no money"),
          new Rule(
              "funds.insufficient",
              Verdict.DENY,
              "the amount is larger than what the envelope still holds"),
          new Rule(
              "emergency.human",
              Verdict.HOLD,
              "emergency money always goes to a human, however small the amount"),
          new Rule(
              "counterparty.unknown",
              Verdict.HOLD,
              "a first transfer to this counterparty is held until a human vouches for it"),
          new Rule(
              "amount.large",
              Verdict.HOLD,
              "a single move over half the remaining envelope is held for a human"),
          new Rule(
              "policy.ok", Verdict.ALLOW, "within the envelope, to a known counterparty, under the limits"));

  private Rulebook() {}

  /** Every rule, deny before hold before allow — the order the engine checks them in. */
  public static List<Rule> rules() {
    return RULES;
  }
}
