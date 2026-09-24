package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.policies.PolicyEngine.Envelope;
import com.hedera.agentplatform.policies.service.PolicyDecisionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * A decision that is not on the ledger is only an opinion. Every verdict the engine returns is
 * recorded through the audit vertical, so "auditable AI" is a thing a judge can open rather than a
 * claim on a slide.
 */
@SpringBootTest
@Transactional
class PolicyDecisionAuditTest {

  @Autowired private PolicyDecisionService decisions;

  private static PolicyState state() {
    return new PolicyState(
        Map.of(Envelope.RENT, 500L, Envelope.EMERGENCY, 200L), List.of("landlord-tunis"));
  }

  @Test
  void every_decision_is_recorded_with_its_rule_id_and_verdict() {
    PolicyDecisionService.RecordedDecision recorded =
        decisions.decide(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), state());

    assertThat(recorded.decision().verdict()).isEqualTo(Verdict.ALLOW);

    AuditEventEntity event = recorded.auditEvent();
    assertThat(event.status).isEqualTo(Verdict.ALLOW.name());
    assertThat(event.payload)
        .as("the rule id is what survives a rewording of the reason")
        .contains("policy.ok");
  }

  @Test
  void a_denied_decision_is_recorded_too_not_only_the_allowed_ones() {
    PolicyDecisionService.RecordedDecision recorded =
        decisions.decide(new PolicyRequest(Envelope.RENT, 9999, "stranger"), state());

    assertThat(recorded.decision().verdict()).isEqualTo(Verdict.DENY);
    assertThat(recorded.auditEvent().status).isEqualTo(Verdict.DENY.name());
    assertThat(recorded.auditEvent().payload).contains("funds.insufficient");
  }

  @Test
  void the_actor_comes_from_the_server_side_resolver_never_from_the_request() {
    PolicyDecisionService.RecordedDecision recorded =
        decisions.decide(new PolicyRequest(Envelope.RENT, 100, "landlord-tunis"), state());

    assertThat(recorded.auditEvent().actorId)
        .as("no user is invented while there is no authentication")
        .isEqualTo("platform");
    assertThat(recorded.auditEvent().actorType).isEqualTo("SYSTEM");
  }

  @Test
  void an_unanchored_decision_is_visible_rather_than_silent() {
    PolicyDecisionService.RecordedDecision recorded =
        decisions.decide(new PolicyRequest(Envelope.EMERGENCY, 5, "landlord-tunis"), state());

    assertThat(recorded.decision().verdict()).isEqualTo(Verdict.HOLD);
    assertThat(recorded.anchored())
        .as("no operator credentials in tests, so the caller must be told it is not on the ledger")
        .isFalse();
    assertThat(recorded.auditEvent().anchorStatus).isNotEqualTo("ANCHORED");
  }
}
