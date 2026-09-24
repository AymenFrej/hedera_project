package com.hedera.agentplatform.accounts.dto;

public record ManagedUserResponse(String id, String email, String displayName, String role, String accountId, String hederaAccountId) {}
