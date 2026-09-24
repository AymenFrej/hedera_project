package com.hedera.agentplatform.policies.repository;
import com.hedera.agentplatform.policies.entity.ApprovalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
  /**
   * Newest first: a human reads the queue from the top.
   *
   * <p>A sequence breaks ties. Two requests submitted in the same millisecond — which happens in
   * tests and in any burst — otherwise come back in whatever order the database chose, so the queue
   * was only usually right. The id cannot break the tie: it is a random UUID, so ordering by it is
   * arbitrary. The insert sequence is the one field that always increases.
   */
  List<ApprovalEntity> findAllByOrderByRequestedAtDescSequenceDesc();

  /** Counterparties a human has actually vouched for, by approving a transfer to them. */
  List<ApprovalEntity> findByStatus(String status);
}
