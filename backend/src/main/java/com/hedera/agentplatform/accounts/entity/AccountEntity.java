package com.hedera.agentplatform.accounts.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id public String id;
    public String hederaAccountId;
    public BigDecimal balance;
    public String status;
}
