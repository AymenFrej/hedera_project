package com.hedera.agentplatform.policies.agent;
import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;
@Component public class PolicyAgent extends PlaceholderAgent { public PolicyAgent() { super("PolicyAgent", AgentIntent.MANAGE_POLICY); } }
