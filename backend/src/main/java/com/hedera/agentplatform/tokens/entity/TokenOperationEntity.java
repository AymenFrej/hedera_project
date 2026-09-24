package com.hedera.agentplatform.tokens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/** One token creation or mint, from the request to the ledger. See V14__tokens_operations.sql. */
@Entity
@Table(name = "token_operations")
public class TokenOperationEntity {

  public enum Kind {
    CREATE,
    MINT
  }

  public enum Status {
    /** Committed with its transaction id, then sent: a crash here is recoverable from the ledger. */
    SUBMITTING,
    CONFIRMED,
    FAILED,
    /** Sent, no receipt: may or may not have reached consensus; the Mirror Node settles it. */
    UNKNOWN,
    /** No Hedera credentials: nothing was sent. */
    SIMULATED
  }

  @Id public String id;

  @Column(nullable = false)
  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  public Kind kind;

  @Column(nullable = false)
  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  public Status status;

  public String tokenId;
  public String name;
  public String symbol;
  public Integer decimals;
  public Long amountUnits;
  public Long maxSupplyUnits;
  public Boolean mintable;
  public String memo;
  public String treasury;
  public String transactionId;
  public String networkStatus;
  public Long totalSupplyAfter;
  public String failureReason;
  public Long feeTinybars;
  public String consensusTimestamp;
  public String idempotencyKey;
  public String requestedByType;
  public String requestedById;

  @Column(nullable = false)
  public OffsetDateTime createdAt;

  @Column(nullable = false)
  public OffsetDateTime updatedAt;

  @Version public long version;
}
