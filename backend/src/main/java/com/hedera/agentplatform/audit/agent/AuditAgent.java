package com.hedera.agentplatform.audit.agent;
import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;
@Component public class AuditAgent extends PlaceholderAgent { public AuditAgent() { super("AuditAgent", AgentIntent.VIEW_AUDIT); } }
