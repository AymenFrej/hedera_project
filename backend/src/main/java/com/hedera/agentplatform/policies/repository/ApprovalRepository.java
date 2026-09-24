package com.hedera.agentplatform.policies.repository;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
  /** Newest first: a human reads the queue from the top. */
  List<ApprovalEntity> findAllByOrderByRequestedAtDesc();

  /** Counterparties a human has actually vouched for, by approving a transfer to them. */
  List<ApprovalEntity> findByStatus(String status);
}
