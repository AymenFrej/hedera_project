package com.hedera.agentplatform.shared.security;

import com.hedera.agentplatform.shared.model.Actor;

/**
 * Resolves who is performing the current request.
 *
 * <p>Extension point for the Accounts module: once authentication exists, provide a bean that reads
 * the session and returns the signed-in user. Until then {@link SystemActorResolver} attributes
 * everything to the platform, which is honest rather than convenient.
 *
 * <p>The contract matters more than the implementation: the actor must come from server-side
 * session state, never from request parameters. An audit trail whose subject is declared by the
 * caller proves nothing.
 */
public interface ActorResolver {

    /** Actor for the request being handled. Never null. */
    Actor currentActor();
}
