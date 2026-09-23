package com.hedera.agentplatform.audit.hedera;

import com.hedera.agentplatform.shared.model.AuditEvent;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real HCS implementation: every audit event becomes a message in a Hedera topic.
 *
 * <p>Only writes go through the SDK. Reads and verification use the Mirror Node REST API, because
 * free SDK queries are throttled on testnet (they answer BUSY) and because reading the proof back
 * from a different channel than the one that wrote it is what makes the verification meaningful.
 */
public class HcsHederaAuditGateway implements HederaAuditGateway {

  private static final Logger log = LoggerFactory.getLogger(HcsHederaAuditGateway.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private final Client client;
  private final TopicId topicId;

  public HcsHederaAuditGateway(Client client, TopicId topicId) {
    this.client = client;
    this.topicId = topicId;
  }

  /**
   * Creates the audit topic. Called once at startup when no topic id is configured.
   *
   * <p>Two deliberate choices, both permanent once the topic exists:
   *
   * <ul>
   *   <li><b>No admin key.</b> HCS messages can never be edited, but a topic that has an admin key
   *       can be <i>deleted</i> by whoever holds it. Leaving it unset makes the topic
   *       indestructible — including by us. It also means the settings below can never be changed
   *       afterwards, which is the point.
   *   <li><b>A submit key.</b> Without one, anyone on the network can post messages into the topic,
   *       so an outsider could inject forged audit events. Setting it to the operator's public key
   *       means only the platform can append to the trail.
   * </ul>
   *
   * <p>Together: nobody can delete the trail, and nobody outside the platform can write to it.
   */
  public static TopicId createTopic(Client client, String memo) throws Exception {
    PublicKey submitKey = client.getOperatorPublicKey();
    if (submitKey == null) {
      throw new IllegalStateException("Cannot create the audit topic without an operator key");
    }

    TransactionResponse response =
        new TopicCreateTransaction()
            .setTopicMemo(memo)
            // No .setAdminKey(...) on purpose: an immutable, undeletable topic.
            .setSubmitKey(submitKey)
            .execute(client, TIMEOUT);

    TopicId created = response.getReceipt(client, TIMEOUT).topicId;
    log.info(
        "Created HCS audit topic {} without an admin key (nobody can delete it) and with a submit"
            + " key (only this platform can append). Pin it with hedera.audit-topic-id to reuse it.",
        created);
    return created;
  }

  @Override
  public AnchorReceipt publish(AuditEvent event) {
    String payload = AuditPayload.canonicalJson(event);
    String payloadHash = AuditPayload.sha256Hex(payload);
    try {
      TransactionResponse response =
          new TopicMessageSubmitTransaction()
              .setTopicId(topicId)
              .setMessage(payload)
              .execute(client, TIMEOUT);

      TransactionReceipt receipt = response.getReceipt(client, TIMEOUT);

      // Consensus timestamp is derived from the transaction id (valid start + 9s offset is
      // not reliable), so read it from the record rather than guessing.
      String consensusTimestamp = response.getRecord(client, TIMEOUT).consensusTimestamp.toString();

      return new AnchorReceipt(
          topicId.toString(),
          response.transactionId.toString(),
          consensusTimestamp,
          receipt.topicSequenceNumber,
          payload,
          payloadHash);
    } catch (Exception e) {
      throw new AuditAnchorException(
          "Could not submit audit event " + event.id() + " to topic " + topicId, e);
    }
  }

  @Override
  public boolean isLive() {
    return true;
  }

  public TopicId topicId() {
    return topicId;
  }
}
