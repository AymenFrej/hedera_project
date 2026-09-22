package com.hedera.agentplatform.accounts.agent;

import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;

@Component
public class AccountAgent extends PlaceholderAgent {
    public AccountAgent() { super("AccountAgent", AgentIntent.CREATE_ACCOUNT); }
}
