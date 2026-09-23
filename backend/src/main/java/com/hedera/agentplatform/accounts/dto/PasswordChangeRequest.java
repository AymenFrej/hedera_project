package com.hedera.agentplatform.accounts.dto;

public record PasswordChangeRequest(String currentPassword, String newPassword) {}
