package com.hedera.agentplatform.payments.repository;

import com.hedera.agentplatform.payments.entity.PaymentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
  List<PaymentEntity> findByStatus(String status);

  Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

  /**
   * Rows of [envelope, sum of amountUnits] for one asset, over payments in the given statuses plus
   * payments already allowed by the policy and about to be sent (PENDING + ALLOW).
   */
  @Query(
      "select p.envelope, sum(p.amountUnits) from PaymentEntity p"
          + " where p.currency = :asset and p.envelope is not null"
          + " and (p.status in :statuses or (p.status = 'PENDING' and p.policyVerdict = 'ALLOW'))"
          + " group by p.envelope")
  List<Object[]> committedByEnvelope(
      @Param("asset") String asset, @Param("statuses") List<String> statuses);

  /** Accounts that received a payment in one of the given statuses. */
  @Query("select distinct p.destination from PaymentEntity p where p.status in :statuses")
  List<String> paidDestinations(@Param("statuses") List<String> statuses);
}
