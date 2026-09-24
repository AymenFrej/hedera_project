package com.hedera.agentplatform.policies.dto;

/** One published rule: its stable id, the verdict it produces, and why in a sentence. */
public record RuleResponse(String ruleId, String verdict, String reason) {}
