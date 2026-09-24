package com.hedera.agentplatform.policies.dto;

/** A spend the agent wants to make. */
public record DecideRequest(String envelope, long amount, String counterparty) {}
