package com.hedera.agentplatform.payments.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Serializes "ask the policy, then commit the verdict" per asset.
 *
 * <p>The policy decides on envelope balances computed from committed payments. Without this, two
 * payments evaluated at the same instant would both see the same balance and could both be allowed,
 * together spending more than the envelope holds. Holding the lock until the verdict is saved means
 * the second evaluation already counts the first payment.
 *
 * <p>In-process: correct for the single backend instance this project runs. Several instances
 * would need a database lock instead (e.g. a Postgres advisory lock per asset).
 */
@Component
class PolicyLocks {

  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  <T> T withLock(String asset, Supplier<T> work) {
    ReentrantLock lock = locks.computeIfAbsent(asset, a -> new ReentrantLock(true));
    lock.lock();
    try {
      return work.get();
    } finally {
      lock.unlock();
    }
  }
}
