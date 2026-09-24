package com.hedera.agentplatform.payments.repository;

import com.hedera.agentplatform.payments.entity.ContactEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<ContactEntity, String> {
  Optional<ContactEntity> findByNameKey(String nameKey);

  List<ContactEntity> findAllByOrderByNameAsc();
}
