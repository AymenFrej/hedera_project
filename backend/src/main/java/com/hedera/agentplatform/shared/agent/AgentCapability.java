package com.hedera.agentplatform.shared.agent;

import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentResult;

public interface AgentCapability {
    boolean supports(AgentIntent intent);

    AgentPlan plan(AgentRequest request);

    AgentResult execute(AgentPlan plan);
}
