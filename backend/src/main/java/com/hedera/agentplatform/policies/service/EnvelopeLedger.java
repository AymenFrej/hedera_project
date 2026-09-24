package com.hedera.agentplatform.policies.service;

import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.PolicyState;
import com.hedera.agentplatform.policies.entity.EnvelopeBalanceEntity;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import com.hedera.agentplatform.policies.repository.EnvelopeBalanceRepository;
import com.hedera.agentplatform.policies.repository.ApprovalRepository;
import java.util.EnumMap;
import java.util.ArrayList;
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
  private static final List<String> SEEDED_COUNTERPARTIES = List.of("landlord-tunis");

  private final EnvelopeBalanceRepository repository;
  private final ApprovalRepository approvals;

  public EnvelopeLedger(EnvelopeBalanceRepository repository, ApprovalRepository approvals) {
    this.repository = repository;
    this.approvals = approvals;
  }

  /** What is left right now, in the shape the engine decides against. */
  public PolicyState state() {
    Map<Envelope, Long> balances = new EnumMap<>(Envelope.class);
    for (EnvelopeBalanceEntity row : repository.findAll()) {
      balances.put(Envelope.valueOf(row.envelope), row.balance);
    }
    return new PolicyState(balances, knownCounterparties());
  }

  /**
   * A counterparty is known once a human has approved a transfer to it. A rejected request leaves
   * it unknown, which is the whole point of asking.
   */
  private List<String> knownCounterparties() {
    List<String> known = new ArrayList<>(SEEDED_COUNTERPARTIES);
    for (ApprovalEntity approval : approvals.findByStatus("APPROVED")) {
      if (approval.counterparty != null && !known.contains(approval.counterparty)) {
        known.add(approval.counterparty);
      }
    }
    return known;
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
