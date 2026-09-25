package com.hedera.agentplatform.payments.repository;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
  List<PaymentEntity> findByStatus(String status);

  Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

}
