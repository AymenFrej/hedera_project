package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TopicId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the audit gateway at startup: the real HCS one when a Hedera client is available, the
 * mock otherwise. A single explicit factory avoids the bean-ordering surprises that come with
 * {@code @ConditionalOnMissingBean} on components.
 */
@Configuration
public class HederaAuditGatewayConfig {

  private static final Logger log = LoggerFactory.getLogger(HederaAuditGatewayConfig.class);

  @Bean
  HederaAuditGateway hederaAuditGateway(
      ObjectProvider<Client> clientProvider, HederaProperties properties) {

    Client client = clientProvider.getIfAvailable();
    if (client == null) {
      log.warn(
          "No Hedera operator credentials: audit events are NOT written to the ledger. "
              + "Set HEDERA_OPERATOR_ID and HEDERA_OPERATOR_PRIVATE_KEY to enable HCS.");
      return new MockHederaAuditGateway();
    }

    try {
      TopicId topicId =
          properties.getAuditTopicId().isBlank()
              ? HcsHederaAuditGateway.createTopic(client, "hedera-agent-platform audit trail")
              : TopicId.fromString(properties.getAuditTopicId().trim());

      log.info("Audit events are anchored to HCS topic {}", topicId);
      return new HcsHederaAuditGateway(client, topicId);
    } catch (Exception e) {
      // Falling back keeps the application usable, but the degradation must be loud.
      log.error(
          "Could not initialise the HCS audit topic, falling back to the mock gateway. "
              + "Audit events will NOT be anchored.",
          e);
      return new MockHederaAuditGateway();
    }
  }
}
