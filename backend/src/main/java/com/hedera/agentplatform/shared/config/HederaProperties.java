package com.hedera.agentplatform.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hedera connection settings, bound from the {@code hedera.*} keys in application.yml.
 *
 * <p>When {@code operatorId} or {@code operatorPrivateKey} is blank the application keeps using the
 * mock gateways, so teammates without a testnet account can still run everything.
 */
@ConfigurationProperties(prefix = "hedera")
public class HederaProperties {

  /** Network to connect to: testnet, previewnet or mainnet. */
  private String network = "testnet";

  /** Operator account, e.g. 0.0.10682427. */
  private String operatorId = "";

  /**
   * Operator private key. Accepts the portal's HEX form (with or without the {@code 0x} prefix) as
   * well as the DER encoded form.
   */
  private String operatorPrivateKey = "";

  /** Key algorithm of the operator account: ECDSA or ED25519. */
  private String keyType = "ECDSA";

  /**
   * Existing HCS topic to publish audit events to. When blank, a topic is created on first use and
   * its id is logged so it can be pinned here afterwards.
   */
  private String auditTopicId = "";

  /** Secret used to encrypt generated user account keys at rest. */
  private String accountKeyEncryptionSecret = "";

  /** Mirror Node REST base URL, used for verification (reads never go through the SDK). */
  private String mirrorNodeUrl = "https://testnet.mirrornode.hedera.com";

  public boolean hasOperatorCredentials() {
    return operatorId != null
        && !operatorId.isBlank()
        && operatorPrivateKey != null
        && !operatorPrivateKey.isBlank();
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  public String getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(String operatorId) {
    this.operatorId = operatorId;
  }

  public String getOperatorPrivateKey() {
    return operatorPrivateKey;
  }

  public void setOperatorPrivateKey(String operatorPrivateKey) {
    this.operatorPrivateKey = operatorPrivateKey;
  }

  public String getKeyType() {
    return keyType;
  }

  public void setKeyType(String keyType) {
    this.keyType = keyType;
  }

  public String getAuditTopicId() {
    return auditTopicId;
  }

  public void setAuditTopicId(String auditTopicId) {
    this.auditTopicId = auditTopicId;
  }

  public String getAccountKeyEncryptionSecret() { return accountKeyEncryptionSecret; }
  public void setAccountKeyEncryptionSecret(String accountKeyEncryptionSecret) { this.accountKeyEncryptionSecret = accountKeyEncryptionSecret; }

  public String getMirrorNodeUrl() {
    return mirrorNodeUrl;
  }

  public void setMirrorNodeUrl(String mirrorNodeUrl) {
    this.mirrorNodeUrl = mirrorNodeUrl;
  }
}
