package com.hedera.agentplatform.audit.agent;
import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;
@Component public class MonitoringAgent extends PlaceholderAgent { public MonitoringAgent() { super("MonitoringAgent", AgentIntent.VIEW_AUDIT); } }
