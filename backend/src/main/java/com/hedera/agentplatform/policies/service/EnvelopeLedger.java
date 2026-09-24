package com.hedera.agentplatform.policies.service;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.PolicyState;
import com.hedera.agentplatform.policies.entity.EnvelopeBalanceEntity;
import com.hedera.agentplatform.policies.repository.EnvelopeBalanceRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The money itself. Balances live in a row so a spend actually leaves the envelope: until this
 * existed the console still showed 500 rent after spending 400 of it, which makes every verdict
 * theatre.
 */
@Service
public class EnvelopeLedger {

  /** The demo relocation runway already has one counterparty paid before. */
  private static final List<String> KNOWN_COUNTERPARTIES = List.of("landlord-tunis");

  private final EnvelopeBalanceRepository repository;

  public EnvelopeLedger(EnvelopeBalanceRepository repository) {
    this.repository = repository;
  }

  /** What is left right now, in the shape the engine decides against. */
  public PolicyState state() {
    Map<Envelope, Long> balances = new EnumMap<>(Envelope.class);
    for (EnvelopeBalanceEntity row : repository.findAll()) {
      balances.put(Envelope.valueOf(row.envelope), row.balance);
    }
    return new PolicyState(balances, KNOWN_COUNTERPARTIES);
  }

  /** Takes the amount out of the envelope. Only called once a spend is settled. */
  public void debit(Envelope envelope, long amount) {
    EnvelopeBalanceEntity row =
        repository
            .findById(envelope.name())
            .orElseThrow(() -> new IllegalArgumentException("Unfunded envelope: " + envelope));
    row.balance = row.balance - amount;
    repository.save(row);
  }
}
