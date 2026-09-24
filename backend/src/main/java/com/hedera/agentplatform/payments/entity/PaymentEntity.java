package com.hedera.agentplatform.payments.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentEntity {
  @Id public String id;

  /** Amount as requested, in HBAR or in token units. For display. */
  public BigDecimal amount;

  /** Amount actually sent: tinybars for HBAR, smallest unit for an HTS token. */
  public Long amountUnits;

  /** "HBAR", or the HTS token id for token transfers. */
  public String currency;

  /** HTS token id, null for HBAR. */
  public String tokenId;

  public String destination;
  public String envelope;
  public String memo;

  /** Account the funds left from. Null until submitted. */
  public String sourceAccount;

  public String transactionId;

  /** See {@link PaymentStatus}. */
  public String status;

  public String policyVerdict;
  public String policyRuleId;
  public String policyReason;
  public String failureReason;

  /** The requester's own condition: at least this much of the asset must remain. */
  public BigDecimal keepAtLeast;

  /** What the envelope held when the policy decided, in smallest units. */
  public Long policyEnvelopeBalance;

  /** Audit event the Policies module recorded the decision as. */
  public String policyAuditEventId;

  /** Approval opened in the Policies module's queue when the decision was HOLD. */
  public String approvalId;

  /** USER, AGENT or SYSTEM: who asked for the payment. Resolved server-side. */
  public String requestedByType;

  public String requestedById;

  /** Client-chosen key: the same key twice returns the first payment instead of paying twice. */
  public String idempotencyKey;

  public Instant createdAt;
  public Instant updatedAt;

  @Version public long version;
}
