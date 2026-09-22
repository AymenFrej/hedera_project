package com.hedera.agentplatform.policies.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "approvals") public class ApprovalEntity { @Id public String id; public String taskId; public String status; public Instant requestedAt; }
