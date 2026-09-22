package com.hedera.agentplatform.shared.model;

import java.util.List;

public record AgentPlan(String planId, String agent, AgentStatus status, List<AgentAction> actions) {}
