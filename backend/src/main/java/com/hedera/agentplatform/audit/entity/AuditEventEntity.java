package com.hedera.agentplatform.audit.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "audit_events") public class AuditEventEntity { @Id public String id; public String agent; public String action; public String status; public Instant createdAt; }
