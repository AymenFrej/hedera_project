package com.hedera.agentplatform.policies.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** What is left in one envelope. */
@Entity
@Table(name = "envelope_balances")
public class EnvelopeBalanceEntity {

  @Id public String envelope;

  public Long balance;
}
