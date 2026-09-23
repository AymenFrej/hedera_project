package com.hedera.agentplatform.audit.hedera;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.shared.model.AuditEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditPayloadTest {

  private static AuditEvent event(Map<String, Object> metadata) {
    return new AuditEvent(
        "audit_1",
        "AuditAgent",
        "TRANSFER",
        "SUCCESS",
        Instant.parse("2026-01-01T00:00:00Z"),
        null,
        metadata);
  }

  @Test
  void canonical_json_is_stable_whatever_the_metadata_order() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("amount", "50");
    first.put("destination", "0.0.123");

    Map<String, Object> second = new LinkedHashMap<>();
    second.put("destination", "0.0.123");
    second.put("amount", "50");

    String a = AuditPayload.canonicalJson(event(first));
    String b = AuditPayload.canonicalJson(event(second));

    assertThat(a).isEqualTo(b);
    assertThat(AuditPayload.sha256Hex(a)).isEqualTo(AuditPayload.sha256Hex(b));
  }

  @Test
  void hash_changes_when_any_field_changes() {
    String original = AuditPayload.canonicalJson(event(Map.of("amount", "50")));
    String tampered = AuditPayload.canonicalJson(event(Map.of("amount", "5000")));

    assertThat(AuditPayload.sha256Hex(original)).isNotEqualTo(AuditPayload.sha256Hex(tampered));
  }

  @Test
  void sha256_is_the_documented_algorithm() {
    // Known vector: SHA-256("abc")
    assertThat(AuditPayload.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void quotes_and_newlines_are_escaped() {
    AuditEvent weird =
        new AuditEvent(
            "audit_2",
            "Agent \"X\"",
            "LINE\nBREAK",
            "SUCCESS",
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            Map.of());

    String json = AuditPayload.canonicalJson(weird);

    assertThat(json).contains("Agent \\\"X\\\"").contains("LINE\\nBREAK");
  }
}
