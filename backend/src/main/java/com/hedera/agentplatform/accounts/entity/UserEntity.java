package com.hedera.agentplatform.accounts.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id public String id;
    public String email;
    public String displayName;
    public String passwordHash;
    public String role;
    public String accountId;
}
