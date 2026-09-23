package com.hedera.agentplatform.shared.model;

/**
 * Who an action is attributed to.
 *
 * <p>This is recorded by the platform, never taken from client input: an audit trail the audited
 * party can shape proves nothing. Callers pass the identity they resolved from the session, and the
 * platform is the one signing and paying for the HCS message.
 *
 * @param type USER, AGENT or SYSTEM
 * @param id stable internal identifier (user id, agent name, service name)
 * @param hederaAccountId Hedera account tied to that actor when there is one, e.g. 0.0.12345;
 *     null when the actor has no account (a system job, an agent without a wallet)
 */
public record Actor(ActorType type, String id, String hederaAccountId) {

    public static Actor system(String id) {
        return new Actor(ActorType.SYSTEM, id, null);
    }

    public static Actor agent(String name) {
        return new Actor(ActorType.AGENT, name, null);
    }

    public static Actor user(String userId, String hederaAccountId) {
        return new Actor(ActorType.USER, userId, hederaAccountId);
    }
}
