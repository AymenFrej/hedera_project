package com.hedera.agentplatform.tokens.repository;

import com.hedera.agentplatform.tokens.entity.TokenOperationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenOperationRepository extends JpaRepository<TokenOperationEntity, String> {

  Optional<TokenOperationEntity> findByIdempotencyKey(String idempotencyKey);

  List<TokenOperationEntity> findTop50ByOrderByCreatedAtDesc();

  List<TokenOperationEntity> findTop50ByRequestedByIdOrderByCreatedAtDesc(String requestedById);

  List<TokenOperationEntity> findByTokenIdOrderByCreatedAtDesc(String tokenId);
}
