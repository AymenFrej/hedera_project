package com.hedera.agentplatform.accounts.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id public String id;
    public String userId;
    public String email;
    public String hederaAccountId;
    public String encryptedPrivateKey;
    public BigDecimal balance;
    public String status;
}
