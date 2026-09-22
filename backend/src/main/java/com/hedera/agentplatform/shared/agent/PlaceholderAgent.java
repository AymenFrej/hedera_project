package com.hedera.agentplatform.shared.agent;

import com.hedera.agentplatform.shared.model.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class PlaceholderAgent implements AgentCapability {
    private final String name;
    private final AgentIntent supportedIntent;

    protected PlaceholderAgent(String name, AgentIntent supportedIntent) {
        this.name = name;
        this.supportedIntent = supportedIntent;
    }

    @Override
    public boolean supports(AgentIntent intent) {
        return supportedIntent == intent;
    }

    @Override
    public AgentPlan plan(AgentRequest request) {
        return new AgentPlan(UUID.randomUUID().toString(), name, AgentStatus.READY,
                List.of(new AgentAction("PLACEHOLDER", "Replace with module planning logic", Map.of())));
    }

    @Override
    public AgentResult execute(AgentPlan plan) {
        return new AgentResult(plan.planId(), AgentStatus.COMPLETED, "Mock execution only", Map.of("mock", true));
    }
}
