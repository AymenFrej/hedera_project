package com.hedera.agentplatform.policies.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A HOLD verdict waiting for a human answer, and the answer once it arrives. */
@Entity
@Table(name = "approvals")
public class ApprovalEntity {

  @Id public String id;

  /** The audit event id of the decision that produced this request. */
  @Column(name = "task_id")
  public String taskId;

  public String status;

  @Column(name = "requested_at")
  public Instant requestedAt;

  /**
   * Insert order, so the queue can break a timestamp tie. Two approvals created in the same
   * millisecond are indistinguishable by requestedAt, and the id is a random UUID, so without this
   * the "newest first" queue was only usually newest first.
   */
  @Column(name = "sequence", insertable = false, updatable = false)
  public Long sequence;

  @Column(name = "rule_id")
  public String ruleId;

  public String reason;
  public String envelope;
  public Long amount;
  public String counterparty;

  @Column(name = "decided_at")
  public Instant decidedAt;

  @Column(name = "decided_by")
  public String decidedBy;
}
