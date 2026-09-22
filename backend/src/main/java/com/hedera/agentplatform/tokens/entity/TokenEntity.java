package com.hedera.agentplatform.tokens.entity;
import jakarta.persistence.*;
@Entity @Table(name = "tokens") public class TokenEntity { @Id public String id; public String symbol; public String name; public String status; }
