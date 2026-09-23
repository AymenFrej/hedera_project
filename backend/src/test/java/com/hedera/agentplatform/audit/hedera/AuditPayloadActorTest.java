package com.hedera.agentplatform.audit.hedera;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.AuditEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditPayloadActorTest {

    private static AuditEvent event(Actor actor) {
        return new AuditEvent(
                "audit_1",
                "PaymentAgent",
                "TRANSFER",
                "SUCCESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                actor,
                Map.of("amount", "50"));
    }

    @Test
    void actor_is_part_of_the_message_written_to_the_ledger() {
        String json = AuditPayload.canonicalJson(event(Actor.user("user_42", "0.0.777")));

        assertThat(json)
                .contains("\"actor\":{\"type\":\"USER\",\"id\":\"user_42\"")
                .contains("\"hederaAccountId\":\"0.0.777\"");
    }

    @Test
    void changing_the_actor_changes_the_hash() {
        String alice = AuditPayload.canonicalJson(event(Actor.user("alice", "0.0.1")));
        String bob = AuditPayload.canonicalJson(event(Actor.user("bob", "0.0.2")));

        assertThat(AuditPayload.sha256Hex(alice)).isNotEqualTo(AuditPayload.sha256Hex(bob));
    }

    @Test
    void an_actor_without_a_hedera_account_is_recorded_as_null_not_invented() {
        String json = AuditPayload.canonicalJson(event(Actor.system("platform")));

        assertThat(json).contains("\"type\":\"SYSTEM\"").contains("\"hederaAccountId\":null");
    }

    @Test
    void a_missing_actor_does_not_break_the_payload() {
        String json = AuditPayload.canonicalJson(event(null));

        assertThat(json).doesNotContain("actor").contains("\"id\":\"audit_1\"");
    }

    @Test
    void canonical_form_stays_stable_for_the_same_actor() {
        String first = AuditPayload.canonicalJson(event(Actor.user("user_42", "0.0.777")));
        String second = AuditPayload.canonicalJson(event(Actor.user("user_42", "0.0.777")));

        assertThat(first).isEqualTo(second);
    }
}
