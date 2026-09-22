package com.hedera.agentplatform.accounts.repository;

import com.hedera.agentplatform.accounts.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {}
