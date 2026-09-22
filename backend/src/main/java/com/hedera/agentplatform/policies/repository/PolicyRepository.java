package com.hedera.agentplatform.policies.repository;
import com.hedera.agentplatform.policies.entity.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PolicyRepository extends JpaRepository<PolicyEntity, String> {}
