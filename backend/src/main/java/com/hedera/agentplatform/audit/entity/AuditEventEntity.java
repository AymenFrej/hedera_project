package com.hedera.agentplatform.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

  @Id public String id;
  public String agent;
  public String action;
  public String status;
  public Instant createdAt;

  /** Exact bytes submitted to the topic; kept so the ledger message can be compared. */
  @Column(columnDefinition = "TEXT")
  public String payload;

  /** SHA-256 of {@link #payload}, hex encoded. */
  public String payloadHash;

  public String topicId;
  public String transactionId;
  public String consensusTimestamp;
  public Long sequenceNumber;

  /** PENDING, ANCHORED or FAILED — see AnchorStatus. */
  public String anchorStatus = AnchorStatus.PENDING.name();
}
