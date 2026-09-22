package com.hedera.agentplatform.audit.repository;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {}
