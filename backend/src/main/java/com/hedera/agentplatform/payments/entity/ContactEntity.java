package com.hedera.agentplatform.payments.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A name someone can be paid by, tied to one Hedera account. */
@Entity
@Table(name = "payment_contacts")
public class ContactEntity {
  @Id public String id;
  public String name;

  /** Lowercased name: contacts are looked up case-insensitively. */
  public String nameKey;

  public String accountId;
  public Instant createdAt;
}
