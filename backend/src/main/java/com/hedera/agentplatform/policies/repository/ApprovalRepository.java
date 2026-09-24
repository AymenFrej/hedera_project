package com.hedera.agentplatform.policies.repository;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
  /** Newest first: a human reads the queue from the top. */
  List<ApprovalEntity> findAllByOrderByRequestedAtDesc();
}
