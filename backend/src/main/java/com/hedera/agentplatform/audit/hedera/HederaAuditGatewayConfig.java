package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.audit.mirror.MirrorNodeClient;
import com.hedera.agentplatform.audit.mirror.TopicGuarantees;
import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.TopicId;
import java.util.Optional;
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
            ObjectProvider<Client> clientProvider,
            HederaProperties properties,
            MirrorNodeClient mirrorNodeClient) {

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
                            ? HcsHederaAuditGateway.createTopic(
                                    client, "hedera-agent-platform audit trail")
                            : TopicId.fromString(properties.getAuditTopicId().trim());

            log.info("Audit events are anchored to HCS topic {}", topicId);
            reportGuarantees(mirrorNodeClient, properties, topicId);
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

    /**
     * Checks what the topic really guarantees instead of assuming it.
     *
     * <p>A topic id can be pinned in configuration by anyone, and a topic created with an admin key
     * can be deleted by whoever holds that key. Saying "the audit trail cannot be erased" without
     * verifying would be a claim we cannot back, so any weakness is logged loudly at startup.
     */
    private void reportGuarantees(
            MirrorNodeClient mirrorNodeClient, HederaProperties properties, TopicId topicId) {

        Optional<TopicGuarantees> guarantees =
                mirrorNodeClient.inspectTopic(properties.getMirrorNodeUrl(), topicId.toString());

        if (guarantees.isEmpty()) {
            log.warn(
                    "Could not verify the guarantees of topic {} via the Mirror Node. Do not claim "
                            + "the audit trail is tamper-proof until this check passes.",
                    topicId);
            return;
        }

        TopicGuarantees g = guarantees.get();
        if (g.deleted()) {
            log.error("Audit topic {} has been DELETED. Events will fail to anchor.", topicId);
            return;
        }
        if (!g.undeletable()) {
            log.warn(
                    "Audit topic {} has an admin key: whoever holds it can DELETE the whole trail. "
                            + "Create a topic without an admin key for a trail nobody can erase.",
                    topicId);
        }
        if (!g.restrictedToPlatform()) {
            log.warn(
                    "Audit topic {} has no submit key: anyone on the network can append forged "
                            + "events to it. Create a topic with a submit key.",
                    topicId);
        }
        if (g.isTrustworthy()) {
            log.info(
                    "Audit topic {} verified: no admin key (nobody can delete it) and a submit key "
                            + "(only this platform can append).",
                    topicId);
        }
    }
}
