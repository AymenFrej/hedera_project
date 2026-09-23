package com.hedera.agentplatform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.audit.mirror.MirrorNodeClient;
import com.hedera.agentplatform.audit.mirror.TopicGuarantees;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks on the real network that the configured audit topic actually offers the guarantees we
 * claim: nobody can delete it, and nobody outside the platform can append to it.
 *
 * <p>Skipped unless HEDERA_AUDIT_TOPIC_ID is set, so it never breaks the build for teammates.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HEDERA_AUDIT_TOPIC_ID", matches = ".+")
class AuditTopicHardeningLiveIT {

    @Autowired private MirrorNodeClient mirrorNodeClient;
    @Autowired private HederaProperties properties;

    @Test
    void the_configured_topic_cannot_be_deleted_and_only_we_can_append() {
        String topicId = properties.getAuditTopicId();

        Optional<TopicGuarantees> guarantees =
                mirrorNodeClient.inspectTopic(properties.getMirrorNodeUrl(), topicId);

        assertThat(guarantees).as("Mirror Node should know topic %s", topicId).isPresent();

        TopicGuarantees g = guarantees.get();
        System.out.println("TOPIC=" + g.topicId());
        System.out.println("UNDELETABLE=" + g.undeletable());
        System.out.println("SUBMIT_RESTRICTED=" + g.restrictedToPlatform());

        assertThat(g.deleted()).as("topic must still exist").isFalse();
        assertThat(g.undeletable())
                .as("topic has an admin key: someone could delete the entire audit trail")
                .isTrue();
        assertThat(g.restrictedToPlatform())
                .as("topic has no submit key: anyone could append forged audit events")
                .isTrue();
    }
}
