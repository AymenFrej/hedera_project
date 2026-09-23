package com.hedera.agentplatform.payments.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentResult;
import com.hedera.agentplatform.shared.model.AgentStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PaymentAgentTest {

  @Autowired private PaymentAgent agent;

  private static AgentRequest request(Map<String, Object> context) {
    return new AgentRequest("req_1", AgentIntent.SEND_PAYMENT, "send 5 hbar to 0.0.4242", context);
  }

  @Test
  void plans_and_executes_a_structured_payment() {
    AgentPlan plan = agent.plan(request(Map.of("destination", "0.0.4242", "amount", "5")));

    assertThat(plan.status()).isEqualTo(AgentStatus.READY);
    assertThat(plan.actions()).extracting("type").containsExactly("CHECK_POLICY", "TRANSFER");

    AgentResult result = agent.execute(plan);
    assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
    assertThat(result.data()).containsEntry("paymentStatus", "SIMULATED");
  }

  @Test
  void refuses_to_plan_a_payment_with_invalid_fields() {
    AgentPlan plan = agent.plan(request(Map.of("destination", "Ahmed", "amount", "5")));

    assertThat(plan.status()).isEqualTo(AgentStatus.FAILED);
    assertThat(agent.execute(plan).status()).isEqualTo(AgentStatus.FAILED);
  }

  @Test
  void only_handles_payment_intents() {
    assertThat(agent.supports(AgentIntent.SEND_PAYMENT)).isTrue();
    assertThat(agent.supports(AgentIntent.CREATE_TOKEN)).isFalse();
  }
}
