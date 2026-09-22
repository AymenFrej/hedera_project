package com.hedera.agentplatform.policies.entity;
import jakarta.persistence.*;
@Entity @Table(name = "policies") public class PolicyEntity { @Id public String id; public String name; public String description; public String status; }
