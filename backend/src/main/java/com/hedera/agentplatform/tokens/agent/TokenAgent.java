package com.hedera.agentplatform.tokens.agent;
import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;
@Component public class TokenAgent extends PlaceholderAgent { public TokenAgent() { super("TokenAgent", AgentIntent.CREATE_TOKEN); } }
