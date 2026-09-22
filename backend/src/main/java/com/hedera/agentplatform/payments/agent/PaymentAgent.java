package com.hedera.agentplatform.payments.agent;

import com.hedera.agentplatform.shared.agent.PlaceholderAgent;
import com.hedera.agentplatform.shared.model.AgentIntent;
import org.springframework.stereotype.Component;

@Component
public class PaymentAgent extends PlaceholderAgent {
    public PaymentAgent() { super("PaymentAgent", AgentIntent.SEND_PAYMENT); }
}
