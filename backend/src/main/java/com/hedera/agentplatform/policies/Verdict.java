package com.hedera.agentplatform.policies;

/** Outcome of a policy decision. The agent never decides alone: HOLD routes to a human. */
public enum Verdict {
  ALLOW,
  HOLD,
  DENY
}
