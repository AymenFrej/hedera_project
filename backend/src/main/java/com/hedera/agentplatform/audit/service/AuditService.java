package com.hedera.agentplatform.audit.service;

import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.audit.entity.AnchorStatus;
import com.hedera.agentplatform.audit.entity.AuditEventEntity;
import com.hedera.agentplatform.audit.hedera.AnchorReceipt;
import com.hedera.agentplatform.audit.hedera.AuditPayload;
import com.hedera.agentplatform.audit.hedera.HederaAuditGateway;
import com.hedera.agentplatform.audit.mirror.MirrorNodeClient;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.audit.repository.AuditEventRepository;
import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.model.AuditEvent;
import com.hedera.agentplatform.shared.security.ActorResolver;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Records agent activity, anchors it to Hedera, and can prove afterwards that a stored event really
 * is on the ledger.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditEventRepository repository;
  private final HederaAuditGateway gateway;
  private final MirrorNodeClient mirrorNodeClient;
  private final HederaProperties properties;
  private final ActorResolver actorResolver;

  public AuditService(
      AuditEventRepository repository,
      HederaAuditGateway gateway,
      MirrorNodeClient mirrorNodeClient,
      HederaProperties properties,
      ActorResolver actorResolver) {
    this.repository = repository;
    this.gateway = gateway;
    this.mirrorNodeClient = mirrorNodeClient;
    this.properties = properties;
    this.actorResolver = actorResolver;
  }

  /**
   * Records an agent action and submits it to HCS, attributing it to the current actor.
   *
   * <p>The local row is written first and kept even when the submission fails, so a failure is
   * visible instead of silently dropped.
   */
  public AuditEventEntity record(
      String agent, String action, String status, Map<String, Object> metadata) {
    return record(agent, action, status, actorResolver.currentActor(), metadata);
  }

  /**
   * Same, with an explicit actor. For callers that already resolved the identity themselves, such
   * as a background job acting on behalf of a user.
   *
   * <p>The actor is written into the message content, not carried by the transaction payer: the
   * platform signs and pays for every audit message, so the trail never depends on the audited
   * party's willingness or HBAR balance.
   */
  public AuditEventEntity record(
      String agent, String action, String status, Actor actor, Map<String, Object> metadata) {

    AuditEventEntity entity = new AuditEventEntity();
    entity.id = "audit_" + UUID.randomUUID();
    entity.agent = agent;
    entity.action = action;
    entity.status = status;
    entity.createdAt = Instant.now();
    entity.anchorStatus = AnchorStatus.PENDING.name();
    if (actor != null) {
      entity.actorType = actor.type() == null ? null : actor.type().name();
      entity.actorId = actor.id();
      entity.actorHederaAccountId = actor.hederaAccountId();
    }

    AuditEvent event =
        new AuditEvent(entity.id, agent, action, status, entity.createdAt, actor, metadata);
    entity.payload = AuditPayload.canonicalJson(event);
    entity.payloadHash = AuditPayload.sha256Hex(entity.payload);
    repository.save(entity);

    try {
      AnchorReceipt receipt = gateway.publish(event);
      entity.topicId = receipt.topicId();
      entity.transactionId = receipt.transactionId();
      entity.consensusTimestamp = receipt.consensusTimestamp();
      entity.sequenceNumber = receipt.sequenceNumber();
      entity.anchorStatus =
          gateway.isLive() ? AnchorStatus.ANCHORED.name() : AnchorStatus.PENDING.name();
    } catch (RuntimeException e) {
      entity.anchorStatus = AnchorStatus.FAILED.name();
      log.error("Audit event {} could not be anchored", entity.id, e);
    }

    return repository.save(entity);
  }

  /**
   * Checks a stored event against the ledger by reading it back from the Mirror Node and comparing
   * hashes. This is the part that turns "we wrote to a blockchain" into "we can prove this event
   * exists".
   */
  public VerificationResult verify(String auditEventId) {
    AuditEventEntity entity =
        repository
            .findById(auditEventId)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown audit event: " + auditEventId));

    if (!AnchorStatus.ANCHORED.name().equals(entity.anchorStatus)
        || entity.topicId == null
        || entity.sequenceNumber == null) {
      return VerificationResult.notAnchored();
    }

    String explorerUrl = explorerUrl(entity.topicId);
    Optional<MirrorNodeClient.MirrorMessage> message =
        mirrorNodeClient.findMessage(
            properties.getMirrorNodeUrl(), entity.topicId, entity.sequenceNumber);

    if (message.isEmpty()) {
      return VerificationResult.failure(
          "Mirror Node has no message %d on topic %s yet"
              .formatted(entity.sequenceNumber, entity.topicId),
          entity.payloadHash,
          explorerUrl);
    }

    MirrorNodeClient.MirrorMessage found = message.get();
    String ledgerHash = AuditPayload.sha256Hex(found.content());

    if (!ledgerHash.equals(entity.payloadHash)) {
      // The database and the ledger disagree. The ledger cannot have changed, so the local record
      // was altered after it was anchored.
      return new VerificationResult(
          false,
          "Ledger content does not match the stored payload: the local record was altered after"
              + " it was anchored",
          entity.payloadHash,
          ledgerHash,
          entity.payload,
          found.content(),
          found.consensusTimestamp(),
          explorerUrl);
    }

    return new VerificationResult(
        true,
        "Ledger message matches the stored payload",
        entity.payloadHash,
        ledgerHash,
        entity.payload,
        found.content(),
        found.consensusTimestamp(),
        explorerUrl);
  }

  public List<AuditEventResponse> findAll() {
    return repository.findAll().stream()
        .sorted(
            Comparator.comparing(
                (AuditEventEntity e) -> e.createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .map(AuditService::toResponse)
        .toList();
  }

  public AuditEventResponse findById(String id) {
    return repository
        .findById(id)
        .map(AuditService::toResponse)
        .orElseThrow(() -> new IllegalArgumentException("Unknown audit event: " + id));
  }

  /** True when audit events are really being written to Hedera. */
  public boolean isLedgerActive() {
    return gateway.isLive();
  }

  private String explorerUrl(String topicId) {
    return "https://hashscan.io/%s/topic/%s".formatted(properties.getNetwork(), topicId);
  }

  private static AuditEventResponse toResponse(AuditEventEntity entity) {
    return new AuditEventResponse(
        entity.id,
        entity.agent,
        entity.action,
        entity.status,
        entity.createdAt,
        entity.actorType,
        entity.actorId,
        entity.actorHederaAccountId,
        entity.anchorStatus,
        entity.topicId,
        entity.transactionId,
        entity.consensusTimestamp,
        entity.sequenceNumber,
        entity.payloadHash);
  }
}
