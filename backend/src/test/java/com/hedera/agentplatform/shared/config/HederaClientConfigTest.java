package com.hedera.agentplatform.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hashgraph.sdk.PrivateKey;
import org.junit.jupiter.api.Test;

class HederaClientConfigTest {

    /**
     * Not a real key: a valid-looking 32-byte ECDSA scalar used only to exercise parsing. Never put
     * an actual operator key in the repository, even a testnet one.
     */
    private static final String HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private static HederaProperties properties(String key) {
    HederaProperties p = new HederaProperties();
    p.setKeyType("ECDSA");
    p.setOperatorPrivateKey(key);
    return p;
  }

  @Test
  void accepts_the_hex_form_shown_by_the_portal() {
    PrivateKey parsed = HederaClientConfig.parsePrivateKey(properties(HEX));
    assertThat(parsed).isNotNull();
  }

  @Test
  void accepts_the_same_key_with_the_0x_prefix() {
    PrivateKey withPrefix = HederaClientConfig.parsePrivateKey(properties("0x" + HEX));
    PrivateKey withoutPrefix = HederaClientConfig.parsePrivateKey(properties(HEX));

    assertThat(withPrefix.toStringRaw()).isEqualTo(withoutPrefix.toStringRaw());
  }

  @Test
  void surrounding_whitespace_does_not_break_parsing() {
    PrivateKey parsed = HederaClientConfig.parsePrivateKey(properties("  0x" + HEX + "  "));
    assertThat(parsed).isNotNull();
  }

  @Test
  void rejects_a_malformed_key_instead_of_failing_later_at_runtime() {
    assertThatThrownBy(() -> HederaClientConfig.parsePrivateKey(properties("not-a-key")))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void blank_credentials_are_reported_as_missing() {
    HederaProperties p = new HederaProperties();
    assertThat(p.hasOperatorCredentials()).isFalse();

    p.setOperatorId("0.0.1");
    assertThat(p.hasOperatorCredentials()).isFalse();

    p.setOperatorPrivateKey(HEX);
    assertThat(p.hasOperatorCredentials()).isTrue();
  }
}
