package com.hedera.agentplatform.shared.model;

/** Nature of the actor an audit event is attributed to. */
public enum ActorType {
    /** A human using the platform, identified by the session. */
    USER,
    /** An agent acting on someone's behalf. */
    AGENT,
    /** The platform itself: scheduled jobs, startup tasks, internal processes. */
    SYSTEM
}
