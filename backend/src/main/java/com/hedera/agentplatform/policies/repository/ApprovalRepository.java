package com.hedera.agentplatform.policies.repository;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {}
