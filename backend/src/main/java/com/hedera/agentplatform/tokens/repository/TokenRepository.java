package com.hedera.agentplatform.tokens.repository;
import com.hedera.agentplatform.tokens.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TokenRepository extends JpaRepository<TokenEntity, String> {}

