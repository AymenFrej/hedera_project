package com.hedera.agentplatform.accounts.dto;

public record CreateManagedUserRequest(String email, String password, String displayName, String role) {}
