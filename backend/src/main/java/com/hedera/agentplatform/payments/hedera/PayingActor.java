package com.hedera.agentplatform.payments.hedera;

import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Whose wallet a transfer leaves from. By default the signed-in person making the request; while a
 * payment is being sent, the payment's own payer instead. That matters for a held payment: it is
 * sent during the reviewer's request, but must leave from the requester's wallet, never from the
 * reviewer's. A treasury top-up is sent as the platform.
 */
@Component
public class PayingActor {

  private static final ThreadLocal<Actor> PAYER = new ThreadLocal<>();

  private final ActorResolver actors;

  public PayingActor(ActorResolver actors) {
    this.actors = actors;
  }

  public Actor current() {
    Actor payer = PAYER.get();
    return payer != null ? payer : actors.currentActor();
  }

  /** Runs {@code work} with {@code payer} as the account funds leave from. */
  public <T> T as(Actor payer, Supplier<T> work) {
    Actor previous = PAYER.get();
    PAYER.set(payer);
    try {
      return work.get();
    } finally {
      if (previous == null) {
        PAYER.remove();
      } else {
        PAYER.set(previous);
      }
    }
  }
}
