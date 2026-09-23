package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.shared.model.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Builds the exact string submitted to HCS, and its hash. */
public final class AuditPayload {

  private AuditPayload() {}

  /**
   * Canonical JSON for an audit event: fields in a fixed order so the same event always produces
   * the same bytes, and therefore the same hash. Metadata keys are sorted for the same reason.
   */
  public static String canonicalJson(AuditEvent event) {
    StringBuilder json = new StringBuilder(128);
    json.append("{\"id\":").append(quote(event.id()));
    json.append(",\"agent\":").append(quote(event.agent()));
    json.append(",\"action\":").append(quote(event.action()));
    json.append(",\"status\":").append(quote(event.status()));
    json.append(",\"createdAt\":")
        .append(quote(event.createdAt() == null ? null : event.createdAt().toString()));

    Map<String, Object> metadata = event.metadata();
    if (metadata != null && !metadata.isEmpty()) {
      json.append(",\"metadata\":{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : new TreeMap<>(metadata).entrySet()) {
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append(quote(entry.getKey()))
            .append(':')
            .append(quote(String.valueOf(entry.getValue())));
      }
      json.append('}');
    }
    return json.append('}').toString();
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required but unavailable", e);
    }
  }

  private static String quote(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder out = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
