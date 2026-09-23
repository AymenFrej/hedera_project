package com.hedera.agentplatform.audit.mirror;

/**
 * What a topic actually guarantees, as reported by the Mirror Node.
 *
 * <p>Checked rather than assumed: a topic id can be pinned in configuration by anyone, and a topic
 * created with an admin key can be deleted. Claiming "the trail cannot be erased" without looking
 * would be a claim we cannot back.
 *
 * @param topicId the topic that was inspected
 * @param undeletable true when the topic has no admin key, so nobody can delete it
 * @param restrictedToPlatform true when the topic has a submit key, so outsiders cannot append
 *     forged events
 * @param deleted true when the topic has already been deleted
 */
public record TopicGuarantees(
        String topicId, boolean undeletable, boolean restrictedToPlatform, boolean deleted) {

    /** True when the topic gives both guarantees an audit trail needs. */
    public boolean isTrustworthy() {
        return undeletable && restrictedToPlatform && !deleted;
    }
}
