package com.hedera.agentplatform.accounts.dto;

public record AuthResponse(String token, String userId, String email, String displayName, String role, AccountResponse account) {}
