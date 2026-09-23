package com.hedera.agentplatform.audit.mirror;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicGuaranteesTest {

    @Test
    void a_topic_is_trustworthy_only_without_admin_key_and_with_submit_key() {
        assertThat(new TopicGuarantees("0.0.1", true, true, false).isTrustworthy()).isTrue();
    }

    @Test
    void an_admin_key_means_someone_can_delete_the_whole_trail() {
        assertThat(new TopicGuarantees("0.0.1", false, true, false).isTrustworthy()).isFalse();
    }

    @Test
    void no_submit_key_means_outsiders_can_forge_events() {
        assertThat(new TopicGuarantees("0.0.1", true, false, false).isTrustworthy()).isFalse();
    }

    @Test
    void a_deleted_topic_guarantees_nothing() {
        assertThat(new TopicGuarantees("0.0.1", true, true, true).isTrustworthy()).isFalse();
    }
}
