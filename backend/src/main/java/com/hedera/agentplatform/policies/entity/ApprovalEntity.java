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
