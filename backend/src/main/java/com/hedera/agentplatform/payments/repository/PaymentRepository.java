package com.hedera.agentplatform.payments.repository;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {}
