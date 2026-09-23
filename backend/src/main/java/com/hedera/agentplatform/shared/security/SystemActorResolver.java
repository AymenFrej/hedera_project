package com.hedera.agentplatform.shared.security;

import com.hedera.agentplatform.shared.model.Actor;

/**
 * Default resolver used while the platform has no authentication.
 *
 * <p>Attributes every action to the platform itself. It does not invent a user: as long as there is
 * no login, claiming an action belongs to someone would be a lie written to an immutable ledger.
 */
public class SystemActorResolver implements ActorResolver {

    private static final Actor PLATFORM = Actor.system("platform");

    @Override
    public Actor currentActor() {
        return PLATFORM;
    }
}
