package com.hedera.agentplatform.policies.repository;

import com.hedera.agentplatform.policies.entity.EnvelopeBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvelopeBalanceRepository extends JpaRepository<EnvelopeBalanceEntity, String> {}
